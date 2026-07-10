"""
Fraud graph API — investigator-role-only.

GET /api/v1/graph/ring/{phone}
  -> Calls graph_service.find_mule_ring, returns subgraph JSON shaped for
     a force-directed graph render (React investigator screen).
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel

from app.core.security import require_role
from app.services.graph_service import GraphService, RingSubgraph

router = APIRouter(prefix="/graph", tags=["graph"])


class GraphNodeOut(BaseModel):
    id: str
    label: str
    group: str


class GraphEdgeOut(BaseModel):
    source: str
    target: str
    relationship: str


class RingSubgraphOut(BaseModel):
    nodes: list[GraphNodeOut]
    edges: list[GraphEdgeOut]
    node_count: int
    edge_count: int


@router.get("/ring/{phone}", response_model=RingSubgraphOut)
async def get_mule_ring(
    phone: str,
    depth: int = 3,
    claims: dict = Depends(require_role("investigator")),
) -> RingSubgraphOut:
    """
    Return the mule-ring subgraph reachable from `phone` within `depth` hops.

    Investigator role required (JWT role=investigator).
    Response is shaped for a force-directed graph (React D3/Sigma.js).
    """
    if depth < 1 or depth > 6:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "depth must be 1–6")

    gs = GraphService()
    try:
        subgraph: RingSubgraph = await gs.find_mule_ring(phone, depth=depth)
    except Exception as e:
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, f"Graph query failed: {e}")
    finally:
        await gs.close()

    return RingSubgraphOut(
        nodes=[GraphNodeOut(id=n.id, label=n.label, group=n.group) for n in subgraph.nodes],
        edges=[GraphEdgeOut(source=e.source, target=e.target, relationship=e.relationship) for e in subgraph.edges],
        node_count=len(subgraph.nodes),
        edge_count=len(subgraph.edges),
    )
