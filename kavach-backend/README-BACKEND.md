# Kavach Backend

Multi-layer fraud protection backend for elderly digital-arrest scam victims.

For the comprehensive system architecture, technical handover details, API reference, and frontend integration guide, please refer to the main **[README.md](../README.md)** in the root folder.

## Stack
Python 3.12 / MySQL 8.0 / Redis 7 / Neo4j 5 / MinIO / Celery / LiteLLM / LangGraph

## Quickstart

```bash
cp .env.example .env    # fill in API keys
docker-compose up -d --build
docker-compose exec api alembic upgrade head
curl localhost:8000/health
```

## Phase Status
- [x] Phase 0 — scaffold, infra, /health
- [x] Phase 1 — WhatsApp bot, rules engine, RAG, classifier
- [x] Phase 2 — guardian alert mesh, Android signal ingestion
- [x] Phase 3 — audio deep-check, LangGraph chain, evidence packages
- [x] Phase 4 — billing stubs, subscription webhook, rate limiting, CORS
