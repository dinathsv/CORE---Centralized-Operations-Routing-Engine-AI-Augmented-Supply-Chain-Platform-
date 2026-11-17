# CORE operations assistant

Python 3.12+ is recommended. The assistant uses Chroma **1.5.9** via its HTTP v2
API, with 512-dimensional deterministic keyword vectors. It needs no embedding
model downloads, Google account, or API key for document retrieval.

```bash
python3 -m venv venv
venv/bin/pip install -r requirements.txt
cp .env.example .env
venv/bin/uvicorn main:app --reload --port 8090
```

Start Chroma with `podman-compose up -d --wait --wait-timeout 600 chromadb` from
the repository root; see the root README for Podman prerequisites. The local `.env` is loaded on startup.
If Chroma is unavailable, the process still starts, `/health` returns 503 with
`status: degraded`, and document operations return a safe 503. Each subsequent
request reconnects automatically. Storage requests time out after 3 seconds to
connect or 8 seconds to read, and run outside the async event loop.

Documents are uploaded from the UI or with the following API. Reusing an ID
replaces its text and source; storage is durable in the Chroma volume.

```json
POST /documents
{"collection":"operations","documents":[{"id":"policy-1","source":"Stock policy","text":"Keep twenty spare pumps in the north warehouse."}]}
```

```json
POST /query
{"collection":"operations","query":"Where are spare pumps kept?"}
```

The response includes `answer`, `sources`, and `mode` (`retrieval`, `generation`,
or `no_results`). Empty/missing collections and unrelated questions produce an
explicit no-results answer. The default answer quotes matching document
excerpts. This is keyword retrieval with simple plural normalization; it does
not infer synonyms or query live inventory/order databases. Collection names
are isolated, and existing collections with a different embedding format
return 409 rather than mixing incompatible vectors.

To enable optional Gemini answers grounded in the same excerpts, set both
`GOOGLE_API_KEY` and `GOOGLE_MODEL` in `.env`. Select a model available to your
Google account. The example uses `gemini-3.8-flash`, listed as stable in the
[Google model catalog](https://aistudio.google.com/docs/models). The integration
uses [LangChain's Google chat adapter](https://docs.langchain.com/oss/python/integrations/chat/google_generative_ai).
Provider failure falls back to excerpts with a warning. Provider errors and
credentials are never returned to callers.

Limits: 100 documents per batch, 20,000 characters per document, 128 characters
per ID, 256 per source, 4,000 per query, and five retrieved documents per answer.
The gateway protects the API; direct port 8090 is intended for local development.

Run offline API and HTTP-contract tests from this directory:

```bash
venv/bin/python -m unittest discover -s tests -v
```

The tests use an in-memory Chroma protocol fixture and mock generation. A real
Chroma server and Google credentials are needed for live integration checks.
