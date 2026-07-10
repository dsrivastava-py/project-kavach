"""
Evidence package builder.

Two responsibilities:
  1. append_hash_chain_event — tamper-evident SHA-256 chain written to
     evidence_packages.hash_chain (JSONB list). Every meaningful state change
     on an incident gets a link in this chain; a single altered event breaks
     every subsequent hash.

  2. generate_evidence_pdf — renders an HTML template with incident details
     via WeasyPrint (HTML/CSS → PDF). Uploads to MinIO, stores pdf_ref.

LEGAL NOTE (flag for Devansh):
  The Section 65B certificate block in the PDF is boilerplate based on the
  standard template for computer-output admissibility under the Indian
  Evidence Act. Do NOT treat this as legally reviewed — have an actual lawyer
  verify the template before presenting it as a real evidentiary document.
"""
from __future__ import annotations

import hashlib
import json
import uuid
from datetime import datetime, timezone
from typing import Any


# ---------------------------------------------------------------------------
# Hash chain
# ---------------------------------------------------------------------------

def _canonical_json(payload: dict) -> str:
    """Stable JSON serialisation — sorted keys, no whitespace."""
    return json.dumps(payload, sort_keys=True, separators=(",", ":"), default=str)


def _compute_link_hash(prev_hash: str, payload: dict) -> str:
    raw = prev_hash + _canonical_json(payload)
    return hashlib.sha256(raw.encode()).hexdigest()


async def append_hash_chain_event(
    incident_id: uuid.UUID,
    event_payload: dict,
    session,  # AsyncSession
) -> str:
    """
    Append a new event to the hash chain for the incident's evidence package.

    If no evidence package exists yet, creates one with an empty chain.
    Returns the new event's hash.

    Call at every meaningful state change:
      - incident opened / risk escalated
      - guardian alerted
      - deep-check completed
      - incident resolved
    """
    from app.models.evidence_package import EvidencePackage
    from sqlalchemy import select

    result = await session.execute(
        select(EvidencePackage).where(EvidencePackage.incident_id == incident_id)
    )
    pkg: EvidencePackage | None = result.scalar_one_or_none()

    if pkg is None:
        pkg = EvidencePackage(
            incident_id=incident_id,
            hash_chain=[],
            pdf_ref="",
            generated_at=datetime.now(timezone.utc),
        )
        session.add(pkg)
        await session.flush()

    chain: list[dict] = pkg.hash_chain if isinstance(pkg.hash_chain, list) else []

    prev_hash = chain[-1]["hash"] if chain else "0" * 64
    event_hash = _compute_link_hash(prev_hash, event_payload)

    chain_entry = {
        "event": event_payload,
        "hash": event_hash,
        "prev_hash": prev_hash,
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }

    pkg.hash_chain = chain + [chain_entry]
    await session.commit()
    return event_hash


def verify_hash_chain(chain: list[dict]) -> bool:
    """
    Verify the integrity of a stored hash chain.
    Returns True if every link is consistent with its predecessor.
    A single modified event will return False for that entry and all after it.
    """
    if not chain:
        return True

    for i, link in enumerate(chain):
        prev_hash = chain[i - 1]["hash"] if i > 0 else "0" * 64
        expected = _compute_link_hash(prev_hash, link["event"])
        if expected != link["hash"]:
            return False
    return True


# ---------------------------------------------------------------------------
# PDF generation
# ---------------------------------------------------------------------------

_HTML_TEMPLATE = """\
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<style>
  body {{ font-family: Arial, sans-serif; font-size: 12px; margin: 40px; }}
  h1 {{ color: #2c3e50; border-bottom: 2px solid #2c3e50; padding-bottom: 8px; }}
  h2 {{ color: #34495e; margin-top: 24px; }}
  table {{ width: 100%; border-collapse: collapse; margin-top: 8px; }}
  th {{ background: #2c3e50; color: white; padding: 6px 8px; text-align: left; }}
  td {{ border: 1px solid #ccc; padding: 6px 8px; }}
  tr:nth-child(even) {{ background: #f9f9f9; }}
  .cert-block {{ border: 2px solid #2c3e50; padding: 16px; margin-top: 32px;
                 background: #eaf0fb; }}
  .flag-badge {{ background: #e74c3c; color: white; padding: 2px 6px;
                 border-radius: 3px; margin-right: 4px; font-size: 11px; }}
  .safe {{ color: #27ae60; font-weight: bold; }}
  .scam {{ color: #e74c3c; font-weight: bold; }}
  .suspicious {{ color: #e67e22; font-weight: bold; }}
</style>
</head>
<body>
<h1>KAVACH — Fraud Incident Evidence Package</h1>
<p><strong>Generated:</strong> {generated_at}</p>
<p><strong>Incident ID:</strong> {incident_id}</p>
<p><strong>Status:</strong> {status}</p>
<p><strong>Risk Score:</strong> {risk_score:.2f}</p>
<p><strong>Started:</strong> {started_at}</p>
{resolved_at_block}

<h2>Signal Timeline</h2>
<table>
  <thead>
    <tr><th>Time</th><th>Signal Type</th><th>Details</th></tr>
  </thead>
  <tbody>
    {signal_rows}
  </tbody>
</table>

{deepcheck_block}

<h2>Evidence Hash Chain</h2>
<table>
  <thead>
    <tr><th>#</th><th>Event</th><th>Timestamp</th><th>Hash (SHA-256)</th></tr>
  </thead>
  <tbody>
    {chain_rows}
  </tbody>
</table>

<div class="cert-block">
<h2>Section 65B Certificate (Computer Output)</h2>
<p>
I, the authorised officer of the system generating this document, hereby certify that:
</p>
<ol>
  <li>The computer output (this document and the data contained herein) was produced
      by a computer in regular use for storing or processing data for the purposes
      of the activities of KAVACH Fraud Protection System.</li>
  <li>During the relevant period, the computer was operating properly, or if not,
      any respect in which it was not operating properly or was out of operation
      for any part of that period was not such as to affect the production of the
      document or the accuracy of its contents.</li>
  <li>The information contained in this document reproduces or is derived from
      information supplied to the computer in the ordinary course of those activities.</li>
  <li>The hash chain printed above provides tamper-evidence: any modification to
      a stored event will break subsequent hashes, detectable by re-verification.</li>
</ol>
<p><strong>System:</strong> Kavach Backend v0.1.0</p>
<p><strong>Incident ID:</strong> {incident_id}</p>
<p><strong>Generated at:</strong> {generated_at}</p>
<p style="color:#c0392b; font-size:11px;">
⚠ LEGAL NOTE: This certificate template has not been reviewed by a lawyer.
Do not use as a real evidentiary document without legal review.
</p>
</div>
</body>
</html>
"""


async def generate_evidence_pdf(incident_id: uuid.UUID, session) -> str:
    """
    Render an evidence PDF for the incident and upload to MinIO.
    Returns the MinIO object ref (key) and stores it in evidence_packages.pdf_ref.

    Uses WeasyPrint (HTML/CSS → PDF) — easier to template than reportlab.
    """
    from app.models.evidence_package import EvidencePackage
    from app.models.incident import Incident
    from app.models.signal_event import SignalEvent
    from app.models.deepcheck_session import DeepcheckSession
    from sqlalchemy import select
    import io

    # Load incident
    incident: Incident | None = await session.get(Incident, incident_id)
    if not incident:
        raise ValueError(f"Incident {incident_id} not found")

    # Load signals
    signals_result = await session.execute(
        select(SignalEvent)
        .where(SignalEvent.elder_id == incident.elder_id)
        .where(SignalEvent.occurred_at >= incident.started_at)
        .order_by(SignalEvent.occurred_at)
        .limit(100)
    )
    signals = signals_result.scalars().all()

    # Load deepcheck session (if any)
    dc_result = await session.execute(
        select(DeepcheckSession)
        .where(DeepcheckSession.incident_id == incident_id)
        .order_by(DeepcheckSession.created_at.desc())
        .limit(1)
    )
    deepcheck: DeepcheckSession | None = dc_result.scalar_one_or_none()

    # Load evidence package (hash chain)
    pkg_result = await session.execute(
        select(EvidencePackage).where(EvidencePackage.incident_id == incident_id)
    )
    pkg: EvidencePackage | None = pkg_result.scalar_one_or_none()
    chain: list[dict] = pkg.hash_chain if pkg and isinstance(pkg.hash_chain, list) else []

    now = datetime.now(timezone.utc)

    # Build HTML
    signal_rows = "\n".join(
        f"<tr><td>{sig.occurred_at.strftime('%Y-%m-%d %H:%M:%S UTC')}</td>"
        f"<td>{sig.event_type}</td>"
        f"<td>{json.dumps(sig.payload, default=str)[:200]}</td></tr>"
        for sig in signals
    ) or "<tr><td colspan='3'>No signals recorded</td></tr>"

    resolved_block = ""
    if incident.resolved_at:
        resolved_block = f"<p><strong>Resolved:</strong> {incident.resolved_at}</p>"
        if incident.resolution_note:
            resolved_block += f"<p><strong>Resolution Note:</strong> {incident.resolution_note}</p>"

    deepcheck_block = ""
    if deepcheck and deepcheck.transcript:
        flags_html = "".join(
            f'<span class="flag-badge">{f}</span>'
            for f in (deepcheck.red_flags or {}).get("red_flags", [])
        )
        spoof_score = deepcheck.spoof_score or 0.0
        deepcheck_block = f"""
<h2>Deep-Check Analysis</h2>
<p><strong>Transcript excerpt:</strong></p>
<blockquote>{deepcheck.transcript[:1000]}</blockquote>
<p><strong>Red flags:</strong> {flags_html or 'None'}</p>
<p><strong>AI-voice likelihood (assistive only):</strong> {spoof_score:.0%}</p>
<p style="font-size:11px; color:#7f8c8d;">
⚠ AI-voice score is assistive only. Cannot confirm or deny synthetic voice.
</p>"""

    chain_rows = "\n".join(
        f"<tr><td>{i + 1}</td>"
        f"<td>{link['event'].get('event_type', str(link['event']))[:80]}</td>"
        f"<td>{link['timestamp']}</td>"
        f"<td style='font-family:monospace; font-size:10px'>{link['hash'][:32]}…</td></tr>"
        for i, link in enumerate(chain)
    ) or "<tr><td colspan='4'>No hash chain events</td></tr>"

    html = _HTML_TEMPLATE.format(
        generated_at=now.strftime("%Y-%m-%d %H:%M:%S UTC"),
        incident_id=str(incident_id),
        status=incident.status,
        risk_score=incident.risk_score,
        started_at=incident.started_at.strftime("%Y-%m-%d %H:%M:%S UTC"),
        resolved_at_block=resolved_block,
        signal_rows=signal_rows,
        deepcheck_block=deepcheck_block,
        chain_rows=chain_rows,
    )

    # Render PDF via WeasyPrint
    from weasyprint import HTML as WeasyprintHTML

    pdf_bytes = WeasyprintHTML(string=html).write_pdf()

    # Upload to MinIO
    from app.core.config import get_settings
    from minio import Minio

    s = get_settings()
    client = Minio(
        s.MINIO_ENDPOINT,
        access_key=s.MINIO_ACCESS_KEY,
        secret_key=s.MINIO_SECRET_KEY,
        secure=False,
    )

    # Ensure bucket exists
    if not client.bucket_exists(s.MINIO_BUCKET):
        client.make_bucket(s.MINIO_BUCKET)

    object_key = f"evidence/{incident_id}/report_{now.strftime('%Y%m%d_%H%M%S')}.pdf"
    client.put_object(
        s.MINIO_BUCKET,
        object_key,
        io.BytesIO(pdf_bytes),
        length=len(pdf_bytes),
        content_type="application/pdf",
    )

    # Update or create evidence package record
    if pkg is None:
        pkg = EvidencePackage(
            incident_id=incident_id,
            hash_chain=chain,
            pdf_ref=object_key,
            generated_at=now,
        )
        session.add(pkg)
    else:
        pkg.pdf_ref = object_key
        pkg.generated_at = now

    await session.commit()

    return object_key


def get_signed_url(object_key: str) -> str:
    """Return a pre-signed MinIO download URL valid for 1 hour."""
    from datetime import timedelta
    from app.core.config import get_settings
    from minio import Minio

    s = get_settings()
    client = Minio(
        s.MINIO_ENDPOINT,
        access_key=s.MINIO_ACCESS_KEY,
        secret_key=s.MINIO_SECRET_KEY,
        secure=False,
    )
    return client.presigned_get_object(
        s.MINIO_BUCKET,
        object_key,
        expires=timedelta(hours=1),
    )
