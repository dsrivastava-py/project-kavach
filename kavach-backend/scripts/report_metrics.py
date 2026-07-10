"""
Kavach Phase 4 — Precision/Recall/F1 + Latency Report

Usage (inside Docker container):
    python scripts/report_metrics.py

Outputs:
    - Formatted table to stdout
    - metrics/report_<timestamp>.json

LIMITATION: scam_corpus rows were used for RAG retrieval embedding during
Phase 1 seeding. A clean held-out split was not created at that time. These
samples are therefore NOT independent of the RAG index — recall may be
artificially inflated on corpus items that match their own embeddings.
This is noted as a known limitation in the report JSON.

For false-positive rate we query incidents where resolution='false_positive'.
"""
from __future__ import annotations

import asyncio
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

# Ensure kavach-backend is on sys.path when run via `docker-compose exec api`
sys.path.insert(0, str(Path(__file__).parent.parent))

from sqlalchemy import select, text
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine, async_sessionmaker

from app.core.config import get_settings
from app.services.rules_engine import match_rules
from app.services.classifier import classify_message

# ---------------------------------------------------------------------------
# Synthetic safe messages (negative test set — ground truth = NOT scam)
# ---------------------------------------------------------------------------
_SAFE_MESSAGES = [
    "Hi mum, just wanted to check if you got my parcel? Love you!",
    "Your SBI account statement for June 2026 is ready. Log in at sbi.co.in to view.",
    "Reminder: your doctor appointment is tomorrow at 10 AM at City Hospital.",
    "Happy birthday! Wishing you a wonderful day filled with joy.",
    "IRCTC: Your train PNR 1234567890 is confirmed. Bon voyage!",
    "Amazon: Your order #405-1234567 has been delivered. Rate your experience.",
    "Hi, this is Ramesh from Bajaj Finance. Your loan EMI of ₹5,200 is due on 15th.",
    "The library book 'Wings of Fire' you reserved is now available for pickup.",
    "Power outage in your area from 9 AM to 1 PM tomorrow for maintenance.",
    "Your Aadhaar-linked mobile number update is complete. Visit uidai.gov.in if not done by you.",
]

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _percentile(values: list[float], pct: float) -> float:
    if not values:
        return 0.0
    sorted_vals = sorted(values)
    idx = int(len(sorted_vals) * pct / 100)
    idx = min(idx, len(sorted_vals) - 1)
    return sorted_vals[idx]


def _print_table(rows: list[dict], columns: list[str]) -> None:
    widths = {col: len(col) for col in columns}
    for row in rows:
        for col in columns:
            widths[col] = max(widths[col], len(str(row.get(col, ""))))

    header = " | ".join(col.ljust(widths[col]) for col in columns)
    sep = "-+-".join("-" * widths[col] for col in columns)
    print(header)
    print(sep)
    for row in rows:
        print(" | ".join(str(row.get(col, "")).ljust(widths[col]) for col in columns))


# ---------------------------------------------------------------------------
# Main evaluation
# ---------------------------------------------------------------------------

async def run_report() -> dict[str, Any]:
    s = get_settings()
    engine = create_async_engine(s.DATABASE_URL, echo=False)
    Session = async_sessionmaker(engine, expire_on_commit=False)

    results = []

    async with Session() as session:
        # --- Positive samples: scam_corpus rows (ground truth = scam) ---
        corpus_rows = (await session.execute(
            text("SELECT id, script_text, red_flag_tags FROM scam_corpus LIMIT 25")
        )).fetchall()

        print(f"\nEvaluating {len(corpus_rows)} scam corpus samples + {len(_SAFE_MESSAGES)} safe samples...\n")

        for row in corpus_rows:
            script_text = row.script_text or ""
            if not script_text.strip():
                continue

            t0 = time.monotonic()
            rule_result = match_rules(script_text, language="en")
            rules_short_circuit = rule_result.rule_confidence >= s.HIGH_CONFIDENCE_THRESHOLD

            if rules_short_circuit:
                verdict = "scam"
                confidence = rule_result.rule_confidence
                provider = None
                latency_ms = int((time.monotonic() - t0) * 1000)
                path = "rules_only"
            else:
                # Full classifier path (may call LLM if configured)
                try:
                    vr = await classify_message(
                        text=script_text,
                        voice_ref=None,
                        language="en",
                        sender_phone="_metrics_eval_",
                        session=session,
                    )
                    verdict = vr.verdict
                    confidence = vr.confidence
                    provider = vr.llm_provider_used
                    latency_ms = vr.latency_ms
                    path = "llm" if provider else "rules_degraded"
                except Exception as e:
                    # LLM unavailable — fall back to rules-based verdict
                    verdict = "suspicious" if rule_result.matched_flags else "unclear"
                    confidence = rule_result.rule_confidence
                    provider = None
                    latency_ms = int((time.monotonic() - t0) * 1000)
                    path = "rules_degraded"

            is_positive_pred = verdict in ("scam", "suspicious")
            results.append({
                "sample_id": str(row.id),
                "ground_truth": "scam",
                "predicted": verdict,
                "confidence": round(confidence, 3),
                "latency_ms": latency_ms,
                "path": path,
                "correct": is_positive_pred,
            })

        # --- Negative samples: safe messages (ground truth = safe) ---
        for i, msg in enumerate(_SAFE_MESSAGES):
            t0 = time.monotonic()
            rule_result = match_rules(msg, language="en")
            rules_short_circuit = rule_result.rule_confidence >= s.HIGH_CONFIDENCE_THRESHOLD

            if rules_short_circuit:
                verdict = "scam"
                confidence = rule_result.rule_confidence
                path = "rules_only"
                latency_ms = int((time.monotonic() - t0) * 1000)
            else:
                try:
                    vr = await classify_message(
                        text=msg,
                        voice_ref=None,
                        language="en",
                        sender_phone="_metrics_eval_neg_",
                        session=session,
                    )
                    verdict = vr.verdict
                    confidence = vr.confidence
                    path = "llm" if vr.llm_provider_used else "rules_degraded"
                    latency_ms = vr.latency_ms
                except Exception:
                    verdict = "unclear"
                    confidence = 0.0
                    path = "rules_degraded"
                    latency_ms = int((time.monotonic() - t0) * 1000)

            is_positive_pred = verdict in ("scam", "suspicious")
            results.append({
                "sample_id": f"safe_{i}",
                "ground_truth": "safe",
                "predicted": verdict,
                "confidence": round(confidence, 3),
                "latency_ms": latency_ms,
                "path": path,
                "correct": not is_positive_pred,  # correct if NOT flagged as scam
            })

        # --- False positive rate from resolved incidents ---
        fp_row = await session.execute(
            text(
                "SELECT "
                "  COUNT(*) FILTER (WHERE resolution = 'false_positive' OR status = 'false_positive') AS fp_count,"
                "  COUNT(*) FILTER (WHERE status = 'resolved' OR status = 'false_positive') AS resolved_count "
                "FROM incidents"
            )
        )
        fp_data = fp_row.fetchone()
        fp_count = fp_data.fp_count or 0
        resolved_count = fp_data.resolved_count or 0
        incident_fp_rate = (fp_count / resolved_count) if resolved_count > 0 else None

    # ---------------------------------------------------------------------------
    # Compute metrics
    # ---------------------------------------------------------------------------
    scam_samples = [r for r in results if r["ground_truth"] == "scam"]
    safe_samples = [r for r in results if r["ground_truth"] == "safe"]

    tp = sum(1 for r in scam_samples if r["predicted"] in ("scam", "suspicious"))
    fn = sum(1 for r in scam_samples if r["predicted"] not in ("scam", "suspicious"))
    fp = sum(1 for r in safe_samples if r["predicted"] in ("scam", "suspicious"))
    tn = sum(1 for r in safe_samples if r["predicted"] not in ("scam", "suspicious"))

    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0

    latencies = [r["latency_ms"] for r in results]
    rules_latencies = [r["latency_ms"] for r in results if r["path"] == "rules_only"]
    llm_latencies = [r["latency_ms"] for r in results if r["path"] == "llm"]

    by_path = {}
    for path in ("rules_only", "llm", "rules_degraded"):
        path_samples = [r for r in results if r["path"] == path]
        if path_samples:
            by_path[path] = {
                "count": len(path_samples),
                "correct": sum(1 for r in path_samples if r["correct"]),
                "mean_latency_ms": round(sum(r["latency_ms"] for r in path_samples) / len(path_samples), 1),
            }

    report: dict[str, Any] = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "sample_counts": {
            "scam_corpus": len(scam_samples),
            "safe_synthetic": len(safe_samples),
            "total": len(results),
        },
        "classification_metrics": {
            "precision": round(precision, 4),
            "recall": round(recall, 4),
            "f1": round(f1, 4),
            "tp": tp, "fp": fp, "tn": tn, "fn": fn,
        },
        "latency_ms": {
            "mean": round(sum(latencies) / len(latencies), 1) if latencies else 0,
            "p50": round(_percentile(latencies, 50), 1),
            "p95": round(_percentile(latencies, 95), 1),
            "p99": round(_percentile(latencies, 99), 1),
        },
        "path_breakdown": by_path,
        "incident_false_positive_rate": {
            "fp_count": int(fp_count),
            "resolved_count": int(resolved_count),
            "rate": round(incident_fp_rate, 4) if incident_fp_rate is not None else "no_data",
        },
        "limitations": [
            "Scam corpus rows were used for RAG embedding index during Phase 1 seeding — "
            "not a clean held-out split. Recall may be inflated on corpus-matched samples.",
            "Safe samples are synthetic (10 hardcoded messages) — not drawn from real user traffic.",
            "LLM evaluation may have been skipped if provider keys are not configured; "
            "these samples fall to 'rules_degraded' path.",
        ],
        "samples": results,
    }

    await engine.dispose()
    return report


def main() -> None:
    report = asyncio.run(run_report())

    metrics_dir = Path(__file__).parent.parent / "metrics"
    metrics_dir.mkdir(exist_ok=True)

    ts = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S")
    out_path = metrics_dir / f"report_{ts}.json"
    out_path.write_text(json.dumps(report, indent=2))

    # Print summary table to stdout
    m = report["classification_metrics"]
    lat = report["latency_ms"]

    print("\n" + "=" * 60)
    print("KAVACH SCAM CLASSIFIER — EVALUATION REPORT")
    print("=" * 60)

    summary_rows = [
        {"Metric": "Precision", "Value": f"{m['precision']:.4f}"},
        {"Metric": "Recall",    "Value": f"{m['recall']:.4f}"},
        {"Metric": "F1 Score",  "Value": f"{m['f1']:.4f}"},
        {"Metric": "TP / FP / TN / FN", "Value": f"{m['tp']} / {m['fp']} / {m['tn']} / {m['fn']}"},
        {"Metric": "Mean latency (ms)",  "Value": str(lat["mean"])},
        {"Metric": "P95 latency (ms)",   "Value": str(lat["p95"])},
        {"Metric": "P99 latency (ms)",   "Value": str(lat["p99"])},
    ]
    _print_table(summary_rows, ["Metric", "Value"])

    print("\nPath breakdown:")
    path_rows = []
    for path, stats in report["path_breakdown"].items():
        path_rows.append({
            "Path": path,
            "Samples": str(stats["count"]),
            "Correct": str(stats["correct"]),
            "Mean latency (ms)": str(stats["mean_latency_ms"]),
        })
    _print_table(path_rows, ["Path", "Samples", "Correct", "Mean latency (ms)"])

    fp_info = report["incident_false_positive_rate"]
    fp_rate_str = (
        f"{fp_info['rate']:.2%}" if isinstance(fp_info["rate"], float)
        else fp_info["rate"]
    )
    print(f"\nIncident false-positive rate: {fp_rate_str} ({fp_info['fp_count']}/{fp_info['resolved_count']} resolved incidents)")

    print(f"\nFull report written to: {out_path}")

    print("\nLIMITATIONS:")
    for lim in report["limitations"]:
        print(f"  * {lim}")
    print()


if __name__ == "__main__":
    main()
