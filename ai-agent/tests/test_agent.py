"""API and storage-contract tests; no model, database, or network required."""
import json
import math
import os
import unittest
from unittest.mock import patch

import httpx
from fastapi.testclient import TestClient

import main
from retrieval import (
    ChromaStore, Document, EMBEDDING_FORMAT, StoreConflict, StoreUnavailable, embed, excerpts,
)


class FakeChroma:
    """Minimal Chroma v2 protocol fixture; checks our serialized HTTP contract."""
    def __init__(self):
        self.collections = {}
        self.requests = []

    def handle(self, request):
        self.requests.append(request)
        path = request.url.path
        if path.endswith("/heartbeat"):
            return httpx.Response(200, json={"nanosecond heartbeat": 1})
        payload = json.loads(request.content) if request.content else {}
        if request.method == "POST" and path.endswith("/collections"):
            name = payload["name"]
            self.collections.setdefault(name, {"id": name, "metadata": payload["metadata"], "docs": {}})
            return httpx.Response(200, json=self.collections[name])
        name = path.split("/collections/")[1].split("/")[0]
        collection = self.collections.get(name)
        if not collection:
            return httpx.Response(404, json={"error": "NotFoundError"})
        if request.method == "GET":
            return httpx.Response(200, json=collection)
        if path.endswith("/upsert"):
            for id_, text, metadata, vector in zip(payload["ids"], payload["documents"], payload["metadatas"], payload["embeddings"]):
                collection["docs"][id_] = {"id": id_, "text": text, "metadata": metadata, "vector": vector}
            return httpx.Response(200, json=True)
        if path.endswith("/query"):
            query_vector = payload["query_embeddings"][0]
            ranked = sorted(collection["docs"].values(), key=lambda doc: -sum(a*b for a, b in zip(query_vector, doc["vector"])))[:payload["n_results"]]
            return httpx.Response(200, json={
                "ids": [[doc["id"] for doc in ranked]],
                "documents": [[doc["text"] for doc in ranked]],
                "metadatas": [[doc["metadata"] for doc in ranked]],
            })
        raise AssertionError(f"Unexpected request {request.method} {path}")


class AgentTests(unittest.TestCase):
    def setUp(self):
        self.chroma = FakeChroma()
        self.store = ChromaStore("chroma", 8000, transport=httpx.MockTransport(self.chroma.handle))
        main.app.dependency_overrides[main.get_store] = lambda: self.store
        self.env = patch.dict(os.environ, {"GOOGLE_API_KEY": "", "GOOGLE_MODEL": ""})
        self.env.start()
        self.client = TestClient(main.app)

    def tearDown(self):
        self.client.close()
        main.app.dependency_overrides.clear()
        self.env.stop()

    def ingest(self, text="Keep twenty spare pumps in the north warehouse.", id_="stock-policy", source="Warehouse policy"):
        return self.client.post("/documents", json={"documents": [{"id": id_, "text": text, "source": source}]})

    def test_ingest_and_grounded_retrieval_without_api_key(self):
        response = self.ingest()
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"count": 1, "collection": "operations"})
        response = self.client.post("/query", json={"query": "Where are spare pumps kept?"})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["mode"], "retrieval")
        self.assertIn("twenty spare pumps", response.json()["answer"])
        self.assertEqual(response.json()["sources"], ["Warehouse policy"])

    def test_reusing_document_id_replaces_old_content(self):
        self.ingest(text="Keep twenty pumps.")
        self.ingest(text="Keep thirty pumps.")
        answer = self.client.post("/query", json={"query": "pumps"}).json()["answer"]
        self.assertIn("thirty pumps", answer)
        self.assertNotIn("twenty pumps", answer)
        self.assertEqual(len(self.chroma.collections["operations"]["docs"]), 1)

    def test_missing_collection_has_no_results(self):
        response = self.client.post("/query", json={"query": "pumps"})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["mode"], "no_results")
        self.assertEqual(response.json()["sources"], [])

    def test_unrelated_documents_are_not_presented_as_evidence(self):
        self.ingest(text="All forklifts require annual inspection.")
        response = self.client.post("/query", json={"query": "pumps"})
        self.assertEqual(response.json()["mode"], "no_results")

    def test_health_reports_dependencies(self):
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["chromadb"], "connected")

    def test_unavailable_store_returns_safe_error_and_recovers(self):
        with patch.object(self.store, "heartbeat", side_effect=StoreUnavailable("private-token")):
            response = self.client.get("/health")
        self.assertEqual(response.status_code, 503)
        self.assertNotIn("private-token", response.text)
        self.assertEqual(self.client.get("/health").status_code, 200)
        with patch.object(self.store, "search", side_effect=StoreUnavailable("private-token")):
            response = self.client.post("/query", json={"query": "pumps"})
        self.assertEqual(response.status_code, 503)
        self.assertNotIn("private-token", response.text)

    def test_generation_uses_retrieved_documents(self):
        self.ingest()
        with patch.dict(os.environ, {"GOOGLE_API_KEY": "test-key", "GOOGLE_MODEL": "test-model"}), \
                patch.object(main, "generate_answer", return_value="Pumps are in the north warehouse.") as generate:
            response = self.client.post("/query", json={"query": "pumps"})
        self.assertEqual(response.json()["mode"], "generation")
        self.assertEqual(generate.call_args.args[1][0].source, "Warehouse policy")

    def test_provider_failure_falls_back_without_exposing_error(self):
        self.ingest()
        with patch.dict(os.environ, {"GOOGLE_API_KEY": "test-key", "GOOGLE_MODEL": "test-model"}), \
                patch.object(main, "generate_answer", side_effect=RuntimeError("private-token")):
            response = self.client.post("/query", json={"query": "pumps"})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["mode"], "retrieval")
        self.assertIn("warning", response.json())
        self.assertNotIn("private-token", response.text)

    def test_incompatible_embedding_collection_returns_conflict(self):
        self.chroma.collections["operations"] = {"id": "operations", "metadata": {}, "docs": {}}
        response = self.client.post("/query", json={"query": "pumps"})
        self.assertEqual(response.status_code, 409)

    def test_invalid_queries_and_collection_names_are_rejected(self):
        for body in [{"query": " "}, {"query": "x" * 4001}, {"query": "pumps", "collection": "../secret"}, {"query": "pumps", "extra": True}]:
            with self.subTest(body=str(body)[:80]):
                self.assertEqual(self.client.post("/query", json=body).status_code, 422)

    def test_invalid_document_batches_are_rejected(self):
        doc = {"id": "one", "text": "Content", "source": "Policy"}
        for documents in [[], [doc, doc], [doc] * 101, [{**doc, "text": " "}], [{**doc, "text": "x" * 20001}]]:
            with self.subTest(size=len(documents)):
                self.assertEqual(self.client.post("/documents", json={"documents": documents}).status_code, 422)

    def test_chroma_contract_sets_embedding_format_vectors_and_timeouts(self):
        self.ingest()
        create, upsert = self.chroma.requests
        self.assertEqual(json.loads(create.content)["metadata"]["embedding_format"], EMBEDDING_FORMAT)
        self.assertEqual(json.loads(create.content)["configuration"]["hnsw"]["space"], "cosine")
        self.assertEqual(len(json.loads(upsert.content)["embeddings"][0]), 512)
        self.assertEqual(upsert.extensions["timeout"]["connect"], 3)
        self.assertEqual(upsert.extensions["timeout"]["read"], 8)


class RetrievalTests(unittest.TestCase):
    def test_vectors_are_stable_normalized_and_plural_tolerant(self):
        first = embed("spare pumps")
        self.assertEqual(first, embed("spare pump"))
        self.assertAlmostEqual(math.sqrt(sum(value*value for value in first)), 1)
        self.assertTrue(all(math.isfinite(value) for value in embed("...")))

    def test_excerpts_focus_on_matching_text(self):
        text = "intro " * 300 + "Pumps should stay dry. " + "trailing " * 300
        excerpt = excerpts("pumps", text)
        self.assertIn("Pumps should stay dry", excerpt)
        self.assertLessEqual(len(excerpt), 902)

    def test_http_connection_errors_are_mapped_to_unavailable(self):
        def fail(request):
            raise httpx.ConnectError("private-host", request=request)
        store = ChromaStore("localhost", 8000, transport=httpx.MockTransport(fail))
        with self.assertRaises(StoreUnavailable):
            store.heartbeat()

    def test_http_server_errors_are_mapped_to_unavailable(self):
        store = ChromaStore("localhost", 8000, transport=httpx.MockTransport(lambda _: httpx.Response(500)))
        with self.assertRaises(StoreUnavailable):
            store.search("operations", "pumps")


if __name__ == "__main__":
    unittest.main()
