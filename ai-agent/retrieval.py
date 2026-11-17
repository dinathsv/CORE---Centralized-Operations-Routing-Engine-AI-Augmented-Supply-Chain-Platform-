"""Bounded Chroma v2 HTTP adapter and deterministic keyword embeddings.

The REST contract targets Chroma 1.5.9. Explicit vectors avoid automatic model
downloads and the HTTP-only adapter avoids native ML dependencies in the API.
"""
from __future__ import annotations

import hashlib
import math
import re
from collections import Counter
from typing import Annotated
from urllib.parse import quote

import httpx
from pydantic import BaseModel, ConfigDict, StringConstraints

EMBEDDING_FORMAT = "core-keywords-sha256-v1-512"
DIMENSIONS = 512
COLLECTIONS_PATH = "/api/v2/tenants/default_tenant/databases/default_database/collections"
STOP_WORDS = frozenset("a an and are as at be by can do for from how i in is it of on or our that the their this to was what when where which who will with you your".split())
NonemptyText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]


class Document(BaseModel):
    model_config = ConfigDict(extra="forbid")
    id: Annotated[NonemptyText, StringConstraints(max_length=128)]
    text: Annotated[NonemptyText, StringConstraints(max_length=20000)]
    source: Annotated[NonemptyText, StringConstraints(max_length=256)]


class StoreUnavailable(Exception):
    pass


class StoreConflict(Exception):
    pass


def tokens(text: str) -> list[str]:
    words = re.findall(r"[^\W_]+(?:[-_][^\W_]+)*", text.lower(), flags=re.UNICODE)
    return [word[:-1] if len(word) > 4 and word.endswith("s") else word
            for word in words if word not in STOP_WORDS]


def embed(text: str) -> list[float]:
    """Stable signed term hashing with cosine-normalized vectors."""
    vector = [0.0] * DIMENSIONS
    for word, count in Counter(tokens(text)).items():
        digest = hashlib.sha256(word.encode("utf-8")).digest()
        index = int.from_bytes(digest[:4], "big") % DIMENSIONS
        vector[index] += (1 if digest[4] & 1 else -1) * (1 + math.log(count))
    norm = math.sqrt(sum(value * value for value in vector))
    if not norm:
        vector[0] = 1.0
        return vector
    return [value / norm for value in vector]


def excerpts(query: str, text: str, limit: int = 900) -> str:
    """Return a bounded verbatim window around a matching query term."""
    if len(text) <= limit:
        return text
    query_terms = set(tokens(query))
    matches = list(re.finditer(r"\w+", text))
    position = next((match.start() for match in matches if set(tokens(match.group())) & query_terms), 0)
    start = max(0, position - limit // 4)
    end = min(len(text), start + limit)
    return ("…" if start else "") + text[start:end] + ("…" if end < len(text) else "")


class ChromaStore:
    def __init__(self, host: str, port: int, transport: httpx.BaseTransport | None = None):
        self.base_url = f"http://{host}:{port}"
        self.transport = transport

    def _request(self, method: str, path: str, *, missing_ok: bool = False, **kwargs):
        try:
            with httpx.Client(base_url=self.base_url, timeout=httpx.Timeout(8, connect=3),
                              transport=self.transport, trust_env=False) as client:
                response = client.request(method, path, **kwargs)
            if response.status_code == 404 and missing_ok:
                return None
            response.raise_for_status()
            return response.json() if response.content else None
        except (httpx.HTTPError, ValueError, TypeError) as exc:
            raise StoreUnavailable() from exc

    def heartbeat(self):
        self._request("GET", "/api/v2/heartbeat")

    @staticmethod
    def _check_format(collection):
        if (collection.get("metadata") or {}).get("embedding_format") != EMBEDDING_FORMAT:
            raise StoreConflict()

    def upsert(self, name: str, documents: list[Document]):
        collection = self._request("POST", COLLECTIONS_PATH, json={
            "name": name, "get_or_create": True,
            "metadata": {"embedding_format": EMBEDDING_FORMAT},
            "configuration": {"hnsw": {"space": "cosine"}},
        })
        self._check_format(collection)
        collection_id = quote(str(collection["id"]), safe="")
        self._request("POST", f"{COLLECTIONS_PATH}/{collection_id}/upsert", json={
            "ids": [document.id for document in documents],
            "documents": [document.text for document in documents],
            "metadatas": [{"source": document.source} for document in documents],
            "embeddings": [embed(document.text) for document in documents],
        })

    def search(self, name: str, query: str) -> list[Document]:
        collection = self._request("GET", f"{COLLECTIONS_PATH}/{quote(name, safe='')}", missing_ok=True)
        if collection is None:
            return []
        self._check_format(collection)
        query_terms = set(tokens(query))
        if not query_terms:
            return []
        collection_id = quote(str(collection["id"]), safe="")
        result = self._request("POST", f"{COLLECTIONS_PATH}/{collection_id}/query", json={
            "query_embeddings": [embed(query)], "n_results": 5,
            "include": ["documents", "metadatas", "distances"],
        })
        # Exact overlap prevents hash collisions being presented as evidence.
        # This is keyword retrieval, not semantic understanding.
        documents = (result.get("documents") or [[]])[0]
        metadata = (result.get("metadatas") or [[]])[0]
        ids = (result.get("ids") or [[]])[0]
        return [Document(id=id_, text=text, source=(meta or {}).get("source", id_))
                for id_, text, meta in zip(ids, documents, metadata)
                if text and query_terms.intersection(tokens(text))]
