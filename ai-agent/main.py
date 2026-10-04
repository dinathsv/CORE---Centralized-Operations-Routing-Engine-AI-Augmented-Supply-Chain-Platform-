"""
CORE AI Agent - FastAPI entry point.

Exposes endpoints for the LangChain-powered AI pipeline,
backed by ChromaDB for vector retrieval and Google Generative AI.
"""
from __future__ import annotations

import os
from contextlib import asynccontextmanager

import chromadb
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel


chroma_client: chromadb.HttpClient | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global chroma_client
    chroma_client = chromadb.HttpClient(
        host=os.getenv("CHROMA_HOST", "localhost"),
        port=int(os.getenv("CHROMA_PORT", "8000")),
    )
    yield
    chroma_client = None


app = FastAPI(
    title="CORE AI Agent",
    description="LangChain-powered AI pipeline for the CORE platform",
    version="0.1.0",
    lifespan=lifespan,
)


class QueryRequest(BaseModel):
    query: str
    collection: str = "default"


class QueryResponse(BaseModel):
    answer: str
    sources: list[str] = []


@app.get("/health")
async def health():
    return {"status": "ok", "service": "core-ai-agent"}


@app.post("/query", response_model=QueryResponse)
async def query(request: QueryRequest):
    """Run a retrieval-augmented generation query."""
    if chroma_client is None:
        raise HTTPException(status_code=503, detail="ChromaDB not connected")
    return QueryResponse(
        answer=f"Received query: {request.query}",
        sources=[],
    )
