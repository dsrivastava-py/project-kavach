"""
RAG layer — embed + store scam corpus chunks, retrieve similar for few-shot context.
Embedding model: config EMBEDDING_MODEL (default text-embedding-3-small, 1536-dim).
"""
from __future__ import annotations

import asyncio
from dataclasses import dataclass

import tiktoken
from sqlalchemy import select, text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.logging import log
from app.models.scam_corpus import ScamCorpus

_CHUNK_TOKENS = 500
_CHUNK_OVERLAP = 50
_tokenizer: tiktoken.Encoding | None = None


def _get_tokenizer() -> tiktoken.Encoding:
    global _tokenizer
    if _tokenizer is None:
        _tokenizer = tiktoken.get_encoding("cl100k_base")
    return _tokenizer


def _chunk_text(text_body: str) -> list[str]:
    enc = _get_tokenizer()
    tokens = enc.encode(text_body)
    if len(tokens) <= _CHUNK_TOKENS:
        return [text_body]
    chunks: list[str] = []
    start = 0
    while start < len(tokens):
        end = min(start + _CHUNK_TOKENS, len(tokens))
        chunks.append(enc.decode(tokens[start:end]))
        start += _CHUNK_TOKENS - _CHUNK_OVERLAP
    return chunks


async def _embed(texts: list[str]) -> list[list[float]]:
    import litellm
    s = get_settings()
    resp = await litellm.aembedding(
        model=s.EMBEDDING_MODEL,
        input=texts,
        api_key=s.OPENAI_API_KEY or None,
    )
    return [item["embedding"] for item in resp.data]


async def embed_and_store(
    script_text: str,
    source: str,
    red_flag_tags: list[str],
    session: AsyncSession,
) -> None:
    chunks = _chunk_text(script_text)
    embeddings = await _embed(chunks)
    for chunk, emb in zip(chunks, embeddings):
        row = ScamCorpus(
            source=source,
            script_text=chunk,
            red_flag_tags=red_flag_tags,
            embedding=emb,
        )
        session.add(row)
    await session.commit()
    log.info("corpus_chunk_stored", chunks=len(chunks), source=source)


@dataclass
class ScamCorpusMatch:
    script_text: str
    red_flag_tags: list[str]
    similarity: float


async def retrieve_similar(
    query_text: str,
    k: int = 5,
    session: AsyncSession | None = None,
) -> list[ScamCorpusMatch]:
    if session is None:
        return []
    try:
        embeddings = await _embed([query_text])
        query_vec = embeddings[0]
        # pgvector cosine distance operator: <=>
        result = await session.execute(
            text(
                "SELECT script_text, red_flag_tags, 1 - (embedding <=> :vec) AS similarity "
                "FROM scam_corpus ORDER BY embedding <=> :vec LIMIT :k"
            ),
            {"vec": str(query_vec), "k": k},
        )
        return [
            ScamCorpusMatch(
                script_text=row.script_text,
                red_flag_tags=list(row.red_flag_tags or []),
                similarity=float(row.similarity),
            )
            for row in result
        ]
    except Exception as e:
        log.warning("rag_retrieve_failed", error=str(e))
        return []
