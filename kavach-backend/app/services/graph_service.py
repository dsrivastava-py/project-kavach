"""
Neo4j fraud intelligence graph service.

Thin wrapper over the official neo4j Python driver. No ORM.
All Cypher uses MERGE not CREATE — every method is idempotent.

Graph schema:
  (:PhoneNumber {number, synthetic})
  (:Incident {incident_id, risk_score, synthetic})
  (:Device {device_id, platform, synthetic})
  (:MuleAccount {account_id, bank, synthetic})

Relationships:
  (:PhoneNumber)-[:INVOLVED_IN]->(:Incident)
  (:PhoneNumber)-[:CALLED_FROM]->(:Device)
  (:MuleAccount)-[:LINKED_TO]->(:PhoneNumber)
  (:MuleAccount)-[:LINKED_TO]->(:MuleAccount)   # ring edges
"""
from __future__ import annotations

import uuid
from dataclasses import dataclass, field

from neo4j import AsyncGraphDatabase

from app.core.config import get_settings
from app.core.logging import log


@dataclass
class RingNode:
    id: str
    label: str
    group: str          # "PhoneNumber" | "Device" | "MuleAccount" | "Incident"
    properties: dict = field(default_factory=dict)


@dataclass
class RingEdge:
    source: str
    target: str
    relationship: str


@dataclass
class RingSubgraph:
    nodes: list[RingNode]
    edges: list[RingEdge]


class GraphService:
    """Thin wrapper over the official neo4j Python driver. No ORM."""

    def __init__(self) -> None:
        s = get_settings()
        self._driver = AsyncGraphDatabase.driver(
            s.NEO4J_URI,
            auth=(s.NEO4J_USER, s.NEO4J_PASSWORD),
        )

    async def close(self) -> None:
        await self._driver.close()

    # ------------------------------------------------------------------
    # Sync incident to graph
    # ------------------------------------------------------------------

    async def sync_incident_to_graph(
        self,
        incident_id: uuid.UUID,
        phone_number: str | None = None,
        device_id: str | None = None,
        risk_score: float = 0.0,
    ) -> None:
        """
        MERGE nodes for Incident and optionally PhoneNumber + Device.
        MERGE relationships.
        Writes to graph_sync_log after sync.
        idempotent — safe to call repeatedly as incident evolves.
        """
        inc_str = str(incident_id)

        async with self._driver.session() as session:
            # Merge Incident node
            await session.run(
                "MERGE (i:Incident {incident_id: $id}) "
                "SET i.risk_score = $risk, i.synthetic = false",
                id=inc_str,
                risk=risk_score,
            )

            if phone_number:
                await session.run(
                    "MERGE (p:PhoneNumber {number: $number}) "
                    "SET p.synthetic = false "
                    "WITH p "
                    "MATCH (i:Incident {incident_id: $incident_id}) "
                    "MERGE (p)-[:INVOLVED_IN]->(i)",
                    number=phone_number,
                    incident_id=inc_str,
                )

            if device_id:
                await session.run(
                    "MERGE (d:Device {device_id: $device_id}) "
                    "SET d.synthetic = false "
                    "WITH d "
                    "MATCH (p:PhoneNumber {number: $number}) "
                    "MERGE (p)-[:CALLED_FROM]->(d)",
                    device_id=device_id,
                    number=phone_number or "unknown",
                )

        await self._write_sync_log(incident_id, "incident")
        log.info("graph_incident_synced", incident_id=inc_str)

    # ------------------------------------------------------------------
    # Find mule ring
    # ------------------------------------------------------------------

    async def find_mule_ring(
        self,
        phone_number: str,
        depth: int = 3,
    ) -> RingSubgraph:
        """
        Variable-length Cypher path match up to `depth` hops from the given
        PhoneNumber node through MuleAccount/Device connections.

        Returns RingSubgraph shaped for a force-directed graph render:
          nodes: [{id, label, group}, ...]
          edges: [{source, target, relationship}, ...]

        Never returns raw Neo4j record objects across this boundary.
        """
        cypher = (
            "MATCH path = (start:PhoneNumber {number: $number})"
            "-[*1.." + str(depth) + "]->(connected) "
            "RETURN path"
        )

        nodes_seen: dict[str, RingNode] = {}
        edges_seen: set[tuple] = set()

        async with self._driver.session() as session:
            result = await session.run(cypher, number=phone_number)
            records = [record async for record in result]

        for record in records:
            path = record.get("path")
            if path is None:
                continue
            _extract_path(path, nodes_seen, edges_seen)

        # If the starting node isn't already there, add it alone
        if phone_number not in nodes_seen and not nodes_seen:
            async with self._driver.session() as session:
                r = await session.run(
                    "MATCH (p:PhoneNumber {number: $number}) RETURN p",
                    number=phone_number,
                )
                row = await r.single()
                if row:
                    node = row["p"]
                    nodes_seen[phone_number] = RingNode(
                        id=phone_number,
                        label=phone_number,
                        group="PhoneNumber",
                        properties=dict(node),
                    )

        return RingSubgraph(
            nodes=list(nodes_seen.values()),
            edges=[
                RingEdge(source=s, target=t, relationship=r)
                for (s, t, r) in edges_seen
            ],
        )

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    async def _write_sync_log(self, entity_id: uuid.UUID, entity_type: str) -> None:
        from datetime import datetime, timezone
        from app.core.db import SessionLocal
        from app.models.graph_sync_log import GraphSyncLog

        now = datetime.now(timezone.utc)
        async with SessionLocal() as session:
            session.add(GraphSyncLog(
                entity_type=entity_type,
                entity_id=entity_id,
                neo4j_synced_at=now,
            ))
            await session.commit()


def _node_id(node) -> str:
    """Extract a stable string ID from a Neo4j node."""
    props = dict(node)
    return (
        props.get("number")
        or props.get("incident_id")
        or props.get("device_id")
        or props.get("account_id")
        or str(node.element_id)
    )


def _node_group(node) -> str:
    labels = list(node.labels)
    return labels[0] if labels else "Unknown"


def _extract_path(path, nodes_seen: dict, edges_seen: set) -> None:
    """Walk a Neo4j Path and populate nodes_seen + edges_seen dicts."""
    for node in path.nodes:
        nid = _node_id(node)
        if nid not in nodes_seen:
            group = _node_group(node)
            nodes_seen[nid] = RingNode(
                id=nid,
                label=nid,
                group=group,
                properties=dict(node),
            )

    for rel in path.relationships:
        src_id = _node_id(rel.start_node)
        tgt_id = _node_id(rel.end_node)
        key = (src_id, tgt_id, rel.type)
        edges_seen.add(key)
