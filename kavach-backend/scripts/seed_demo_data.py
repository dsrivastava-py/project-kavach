"""
Seed synthetic mule ring demo data into Neo4j.

Creates a 40-node ring (phone numbers, mule accounts, devices) with plausible
fake linkages for the investigator demo. All nodes carry synthetic=true — they
MUST never be confused with real incident data.

Run: python -m scripts.seed_demo_data

Idempotent: safe to re-run. Uses MERGE everywhere so duplicate runs don't
create duplicate nodes.
"""
from __future__ import annotations

import asyncio
import uuid

from neo4j import AsyncGraphDatabase

from app.core.config import get_settings
from app.core.logging import configure_logging, log

configure_logging()


# ---------------------------------------------------------------------------
# Synthetic data definitions
# ---------------------------------------------------------------------------

# 15 phone numbers in the ring
PHONES = [
    "+919876540001", "+919876540002", "+919876540003", "+919876540004",
    "+919876540005", "+442071838750", "+14155552671", "+919876540006",
    "+919876540007", "+919876540008", "+919876540009", "+919876540010",
    "+8613800138000", "+919876540011", "+919876540012",
]

# 10 mule bank accounts
MULE_ACCOUNTS = [
    {"account_id": f"MULE-ACC-{i:03d}", "bank": bank}
    for i, bank in enumerate([
        "HDFC", "ICICI", "SBI", "Axis", "PNB",
        "Kotak", "YES", "BOB", "Canara", "Union",
    ], 1)
]

# 8 devices (IMEI-style IDs)
DEVICES = [
    {"device_id": f"DEV-{str(uuid.UUID(int=i*7919))[:8].upper()}", "platform": p}
    for i, p in enumerate(["android", "android", "android", "ios", "android",
                            "android", "android", "ios"], 1)
]

# 7 fake incidents (scam calls linked to phones)
INCIDENTS = [
    {"incident_id": f"INC-SYNTH-{i:03d}", "risk_score": score}
    for i, score in enumerate([0.82, 0.91, 0.78, 0.85, 0.93, 0.76, 0.88], 1)
]


# ---------------------------------------------------------------------------
# Seed functions
# ---------------------------------------------------------------------------

async def seed(driver) -> None:
    async with driver.session() as s:
        log.info("seed_start", message="Merging PhoneNumber nodes")
        for phone in PHONES:
            await s.run(
                "MERGE (p:PhoneNumber {number: $number}) "
                "SET p.synthetic = true, p.label = $number",
                number=phone,
            )

        log.info("seed_mule_accounts")
        for acc in MULE_ACCOUNTS:
            await s.run(
                "MERGE (m:MuleAccount {account_id: $account_id}) "
                "SET m.bank = $bank, m.synthetic = true",
                account_id=acc["account_id"],
                bank=acc["bank"],
            )

        log.info("seed_devices")
        for dev in DEVICES:
            await s.run(
                "MERGE (d:Device {device_id: $device_id}) "
                "SET d.platform = $platform, d.synthetic = true",
                device_id=dev["device_id"],
                platform=dev["platform"],
            )

        log.info("seed_incidents")
        for inc in INCIDENTS:
            await s.run(
                "MERGE (i:Incident {incident_id: $incident_id}) "
                "SET i.risk_score = $risk_score, i.synthetic = true",
                incident_id=inc["incident_id"],
                risk_score=inc["risk_score"],
            )

        # --------------- Ring relationships ---------------

        # Core ring: phones form a circular chain through shared mule accounts
        # Phone 0 → MuleAccount 0 → Phone 1 → MuleAccount 1 → ... → back
        log.info("seed_ring_relationships")
        for i, phone in enumerate(PHONES):
            acc = MULE_ACCOUNTS[i % len(MULE_ACCOUNTS)]
            next_phone = PHONES[(i + 1) % len(PHONES)]
            await s.run(
                "MATCH (p:PhoneNumber {number: $phone}) "
                "MATCH (m:MuleAccount {account_id: $acc}) "
                "MERGE (p)-[:LINKED_TO]->(m)",
                phone=phone,
                acc=acc["account_id"],
            )
            await s.run(
                "MATCH (m:MuleAccount {account_id: $acc}) "
                "MATCH (p2:PhoneNumber {number: $next_phone}) "
                "MERGE (m)-[:LINKED_TO]->(p2)",
                acc=acc["account_id"],
                next_phone=next_phone,
            )

        # Phones called from devices
        for i, phone in enumerate(PHONES[:len(DEVICES)]):
            dev = DEVICES[i % len(DEVICES)]
            await s.run(
                "MATCH (p:PhoneNumber {number: $phone}) "
                "MATCH (d:Device {device_id: $device_id}) "
                "MERGE (p)-[:CALLED_FROM]->(d)",
                phone=phone,
                device_id=dev["device_id"],
            )

        # Phones involved in incidents
        for i, inc in enumerate(INCIDENTS):
            phone = PHONES[i % len(PHONES)]
            await s.run(
                "MATCH (p:PhoneNumber {number: $phone}) "
                "MATCH (i:Incident {incident_id: $incident_id}) "
                "MERGE (p)-[:INVOLVED_IN]->(i)",
                phone=phone,
                incident_id=inc["incident_id"],
            )

        # Cross-links between mule accounts (shows the ring tightly)
        for i in range(0, len(MULE_ACCOUNTS) - 1, 2):
            await s.run(
                "MATCH (a:MuleAccount {account_id: $a1}) "
                "MATCH (b:MuleAccount {account_id: $a2}) "
                "MERGE (a)-[:LINKED_TO]->(b)",
                a1=MULE_ACCOUNTS[i]["account_id"],
                a2=MULE_ACCOUNTS[i + 1]["account_id"],
            )

        # Count nodes for verification
        result = await s.run("MATCH (n) WHERE n.synthetic = true RETURN count(n) AS cnt")
        row = await result.single()
        total = row["cnt"] if row else 0
        log.info("seed_complete", synthetic_nodes=total)
        print(f"Seed complete — {total} synthetic nodes in Neo4j.")


async def main() -> None:
    s = get_settings()
    driver = AsyncGraphDatabase.driver(s.NEO4J_URI, auth=(s.NEO4J_USER, s.NEO4J_PASSWORD))
    try:
        await seed(driver)
    finally:
        await driver.close()


if __name__ == "__main__":
    asyncio.run(main())
