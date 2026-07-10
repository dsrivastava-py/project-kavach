"""
Integration test: classifier rules engine + webhook.
Run against live stack with WHATSAPP_PROVIDER=twilio (no real webhook signature needed in test mode).

Usage:
  pytest app/tests/test_classifier.py -v
"""
import time
import pytest
from app.services.rules_engine import match_rules, reload_rules


# --- Rules engine unit tests (no DB needed) ---

KNOWN_SCAM_TEXTS = [
    "Your SIM card will be disconnected by TRAI due to illegal activity. Press 9 to talk to officer.",
    "CBI officer here. You are under digital arrest. Do not move. Pay fine immediately.",
    "Courier parcel seized at customs. Your Aadhar linked to drug shipment. Call immediately.",
    "Your bank account linked to money laundering. Transfer to safe account now to avoid arrest.",
    "TRAI notice: your mobile number will be blocked in 2 hours due to suspicious calls. Verify now.",
]

SAFE_TEXTS = [
    "Hey, are you free this evening for dinner?",
    "Your order has been shipped. Track at amazon.in",
    "Happy birthday! Hope you have a wonderful day.",
]


def test_rules_match_known_scams():
    reload_rules()
    for text in KNOWN_SCAM_TEXTS:
        result = match_rules(text, "en")
        assert result.rule_confidence > 0, f"No match for: {text[:60]}"
        assert len(result.matched_flags) > 0


def test_rules_safe_texts_low_confidence():
    reload_rules()
    for text in SAFE_TEXTS:
        result = match_rules(text, "en")
        # Safe texts should not trigger high confidence
        assert result.rule_confidence < 0.85, f"False positive on: {text[:60]}"


def test_rules_high_confidence_scam():
    text = "CBI officer. You are under digital arrest. Your Aadhar linked to 47 criminal cases. Pay ₹50000 now."
    result = match_rules(text, "en")
    assert result.rule_confidence >= 0.5, f"Expected high confidence, got {result.rule_confidence}"


def test_rules_hindi_scam():
    text = "TRAI से अधिकारी बोल रहे हैं। आपका SIM बंद होगा। अभी 9 दबाएं।"
    result = match_rules(text, "hi")
    # Patterns may or may not match Hindi — just verify no crash
    assert isinstance(result.rule_confidence, float)


# --- Webhook integration test (requires live stack) ---

@pytest.mark.asyncio
async def test_webhook_rejects_unsigned():
    import httpx
    async with httpx.AsyncClient(base_url="http://localhost:8000") as client:
        resp = await client.post(
            "/api/v1/webhooks/whatsapp",
            json={"test": "unsigned"},
            headers={"Content-Type": "application/json"},
        )
    assert resp.status_code == 403, f"Expected 403 for unsigned webhook, got {resp.status_code}"


@pytest.mark.asyncio
async def test_classify_known_scam_via_rules():
    """
    Unit-level test of classifier with mocked session.
    Precision/recall report prints to stdout for pitch deck.
    """
    from unittest.mock import AsyncMock, MagicMock
    from app.services.classifier import classify_message

    mock_session = AsyncMock()
    mock_session.add = MagicMock()
    mock_session.commit = AsyncMock()

    results = []
    labels = []
    t_start = time.monotonic()

    for text in KNOWN_SCAM_TEXTS:
        result = await classify_message(
            text=text, voice_ref=None, language="en",
            sender_phone="+910000000000", session=mock_session,
        )
        results.append(result.verdict)
        labels.append("scam")

    for text in SAFE_TEXTS:
        result = await classify_message(
            text=text, voice_ref=None, language="en",
            sender_phone="+910000000000", session=mock_session,
        )
        results.append(result.verdict)
        labels.append("safe")

    total_ms = int((time.monotonic() - t_start) * 1000)

    # Precision/recall (rules-only; LLM keys not configured in CI)
    scam_indices = [i for i, l in enumerate(labels) if l == "scam"]
    safe_indices = [i for i, l in enumerate(labels) if l == "safe"]

    tp = sum(1 for i in scam_indices if results[i] in ("scam", "suspicious"))
    fp = sum(1 for i in safe_indices if results[i] in ("scam", "suspicious"))
    fn = sum(1 for i in scam_indices if results[i] not in ("scam", "suspicious"))
    tn = sum(1 for i in safe_indices if results[i] not in ("scam", "suspicious"))

    precision = tp / (tp + fp) if (tp + fp) else 0
    recall = tp / (tp + fn) if (tp + fn) else 0

    print(f"\n=== Classifier Precision/Recall Report ===")
    print(f"Scam samples: {len(scam_indices)}  Safe samples: {len(safe_indices)}")
    print(f"TP={tp}  FP={fp}  FN={fn}  TN={tn}")
    print(f"Precision: {precision:.2f}  Recall: {recall:.2f}")
    print(f"Total latency (rules-only, {len(labels)} samples): {total_ms}ms")
    print(f"Avg per sample: {total_ms // len(labels)}ms")
    print(f"Verdicts: {results}")
    # Do NOT hardcode a pass/fail bar — Devansh sets the threshold
