# CORE — Centralized Operations & Routing Engine

CORE is a local supply-chain operations application with a Next.js dashboard, a Spring Cloud API gateway, independent inventory and order services, and a document assistant. PostgreSQL stores business records, Kafka carries domain events, and ChromaDB stores document vectors.

The dashboard includes authenticated access, searchable inventory with create/edit/delete, order creation and lifecycle management, live summary metrics, document ingestion, and answers with source references. Empty and unavailable services are shown explicitly; there is no seeded business data.

## Start with Podman

Install [Podman](https://podman.io/docs/installation) 4.6+ and
[podman-compose](https://github.com/containers/podman-compose/releases/tag/v1.6.0)
1.6.0+ (required for the readiness flags below). For example, with pipx installed:

```bash
pipx install podman-compose==1.6.0
```

On macOS and Windows, initialize and start a [Podman machine](https://docs.podman.io/en/latest/markdown/podman-machine.1.html)
before running the stack (`podman machine init` once, then `podman machine start`).
On Linux, run Podman as your regular user. Then run from this directory:

```bash
cp .env.example .env
# Edit .env if you want different local credentials or optional Gemini generation.
podman-compose up --build -d --wait --wait-timeout 600
```

Open **http://localhost:3000** and sign in with **admin / admin**, or the `CORE_ADMIN_USERNAME` and `CORE_ADMIN_PASSWORD` set in `.env`. The first build downloads images and dependencies and may take several minutes. Application containers include their Node, Java, and Python runtimes.

`podman-compose` reads `compose.yml` and builds each application from its
`Containerfile`, using `.containerignore` to exclude local build artifacts and secrets.

```bash
podman-compose ps
podman-compose logs -f gateway inventory-service orders-service ai-agent frontend
podman-compose down
```

`podman-compose down` preserves database and document volumes. PostgreSQL initialization runs only when its data volume is first created. Changing database credentials in `.env` does not change credentials in an existing database volume. Podman uses separate container and volume storage; data from a previous Docker setup must be backed up and restored separately.

All published ports bind to loopback. Authentication is enforced at the gateway; direct inventory, orders, and vector-store ports are for local development. This configuration is intended for a trusted local workstation. An external deployment needs TLS, restricted internal service access, secret management, and an appropriate identity provider.

## Architecture

```text
Browser :3000
  -> Next.js /api proxy (runtime GATEWAY_URL)
    -> Gateway :8080 (stateless HTTP Basic)
      -> Inventory :8081 -> core_inventory (PostgreSQL)
      -> Orders    :8082 -> core_orders    (PostgreSQL)
      -> AI agent  :8090 -> ChromaDB :8000
                            -> optional Google Gemini
Inventory and Orders -> transactional outbox -> Kafka :29092
```

Browser credentials are held in memory and cleared on refresh or sign-out. The browser uses the same origin for every API call. The Next.js server forwards its Authorization header to the gateway, which authenticates requests and removes that header before forwarding to internal services.

| Component | Technology | Local port |
| --- | --- | --- |
| Frontend | Next.js, React, TypeScript | 3000 |
| Gateway | Spring Boot 3.2.5 / Spring Cloud 2023.0.1, WebFlux Security | 8080 |
| Inventory | Spring Boot, JPA, PostgreSQL, Kafka | 8081 |
| Orders | Spring Boot, JPA, PostgreSQL, Kafka | 8082 |
| Assistant | FastAPI, Chroma v2 HTTP API, optional LangChain Gemini | 8090 |
| Database | PostgreSQL 15 | 5432 |
| Broker | Confluent Kafka 7.5.0 / Zookeeper | 29092 |
| Vector store | ChromaDB 1.5.9 | 8000 |

## Local development

Requirements: Node.js 20.9+ (22 LTS recommended), JDK 17+, Python 3.10+, Podman 4.6+, and podman-compose 1.6.0+. Maven wrappers download Maven 3.9.12 if needed. Dependency downloads require network access.

```bash
./setup_core.sh
# Installs frontend dependencies and ai-agent/venv; preserves existing .env files.
podman-compose up -d --wait --wait-timeout 600 postgres zookeeper kafka chromadb
```

If multiple Python versions are installed, select one with `CORE_PYTHON=python3.12 ./setup_core.sh`. If `JAVA_HOME` is set, it must point to your installed JDK.

Load the root environment before starting the Java services, particularly if you changed database credentials:

```bash
set -a
source .env
set +a
```

Start each process in a separate terminal from the repository root:

```bash
(cd backend/inventory-service && ./mvnw spring-boot:run)
(cd backend/orders-service && ./mvnw spring-boot:run)
(cd gateway && ./mvnw spring-boot:run)
(cd ai-agent && ./venv/bin/uvicorn main:app --reload --port 8090)
(cd frontend && npm run dev)
```

The default gateway targets are localhost ports 8081, 8082, and 8090. Override `INVENTORY_SERVICE_URL`, `ORDERS_SERVICE_URL`, or `AI_SERVICE_URL` when running elsewhere. `frontend/.env.local` can set the server-only `GATEWAY_URL`; the Podman stack sets it to `http://gateway:8080` at runtime.

The AI agent reads `ai-agent/.env`; see its example file. No model API key is required for document retrieval. To use generation, set `GOOGLE_API_KEY` and `GOOGLE_MODEL` (default in the root example: `gemini-3.8-flash`). Provider availability and account access determine which models can be used. Retrieved excerpts remain available if generation fails, and the response identifies the mode.

## API

Use the gateway at `http://localhost:8080`, or the same `/api` routes through the frontend at port 3000. All API routes require HTTP Basic credentials. `/actuator/health` on the gateway and Java services is public; the AI agent's direct health endpoint is `/health`.

| Method | Gateway path | Behavior |
| --- | --- | --- |
| GET / POST | `/api/inventory` | List / create an item |
| GET / PUT / DELETE | `/api/inventory/{id}` | Read / update / remove an item |
| GET / POST | `/api/orders` | List / create an order |
| GET | `/api/orders/{id}` | Read an order |
| PATCH | `/api/orders/{id}/status?status=CONFIRMED` | Change lifecycle status |
| DELETE | `/api/orders/{id}` | Cancel an order, retaining its record |
| POST | `/api/ai/documents` | Upsert a bounded document batch |
| POST | `/api/ai/query` | Retrieve relevant documents and answer |
| GET | `/api/ai/health` | Assistant and vector-store health |

Inventory creation and replacement accept:

```json
{"sku":"CORE-001","name":"Shipping carton","description":"Medium carton","quantity":40,"price":2.50}
```

Orders accept:

```json
{"customerName":"Acme Distribution","sku":"CORE-001","quantity":3}
```

IDs, initial order status, and creation time are assigned by the server. Inventory quantities are nonnegative integers; order quantities are positive integers. Prices use decimal arithmetic and accept at most two decimal places. Invalid input returns 400, missing records 404, and duplicate SKUs or invalid transitions 409, with problem details.

The order workflow is **PENDING → CONFIRMED → SHIPPED → DELIVERED**. PENDING and CONFIRMED orders can be CANCELLED. Repeating the current status is safe. An order's SKU is a reference: creating or changing an order does **not** reserve or decrement stock. Inventory quantities are managed independently.

Document ingestion and retrieval:

```bash
curl -u admin:admin http://localhost:8080/api/ai/documents \
  -H 'Content-Type: application/json' \
  -d '{"collection":"operations","documents":[{"id":"returns-policy","text":"Unopened items can be returned within 30 days.","source":"Returns policy"}]}'

curl -u admin:admin http://localhost:8080/api/ai/query \
  -H 'Content-Type: application/json' \
  -d '{"collection":"operations","query":"What is the returns policy?"}'
```

Ingestion upserts by document ID. The assistant uses deterministic token-hash embeddings, so it needs no embedding API or downloaded model. Retrieval is lexical and does not infer synonyms like a semantic embedding model. Answers include `sources` and `mode`; query responses distinguish no matches from unavailable storage. Documents are provided explicitly; there is no automatic Kafka-to-document ingestion or access to live inventory inside the assistant. See [AI service details](ai-agent/README.md).

## Events and persistence

Inventory and orders each persist a domain change and its event in the same database transaction. A scheduled publisher sends queued events to `inventory-events` or `order-events`, deleting an outbox entry only after Kafka acknowledges it. Broker failures leave entries available for retry, including after restart.

Events contain `eventId`, `type`, `entityType`, `entityId`, `occurredAt`, and `data`. Delivery is at least once; consumers must deduplicate `eventId`. Run one publisher instance per service with this implementation. There is no global ordering across services. See [backend details](backend/README.md).

JPA manages the local schema with `ddl-auto: update`; migration scripts and a durable event-consumer pipeline are outside this local application's scope.

## Verification

After dependency setup:

```bash
./scripts/check.sh
```

This runs the three Java test suites, frontend type checking, production build and proxy tests, AI tests, and Podman Compose validation. Java tests use H2 and disabled scheduling; AI tests use an isolated fake store and HTTP contract mocks, so test execution needs no running infrastructure or API key once dependencies are installed. GitHub Actions runs these checks on push and pull requests.

Against a running stack:

```bash
python3 scripts/smoke_test.py
# To exercise the gateway directly:
python3 scripts/smoke_test.py --base-url http://localhost:8080
```

The smoke test checks authentication, inventory CRUD, duplicate rejection, order transitions, and document ingestion/retrieval. It removes its temporary inventory item and leaves a cancelled order plus an isolated smoke document collection as verification records. If credentials were changed, export `CORE_ADMIN_USERNAME` and `CORE_ADMIN_PASSWORD` first.

## Troubleshooting

- If fish reports `Unknown command: podman-compose`, install the separate Compose tool with `pipx install podman-compose==1.6.0`. If installed but still unavailable, run `fish_add_path ~/.local/bin`, then check `podman-compose --version`.
- A 401 means the credentials differ from the running gateway's configuration. Recreate the gateway after changing `.env`.
- A proxy 502 means the frontend cannot reach `GATEWAY_URL`; local and container addresses differ.
- A 503 from the assistant means its Chroma dependency is unavailable. Check `podman-compose logs chromadb ai-agent`; it can reconnect after the store returns.
- Podman builds require registry, Maven Central, npm, and PyPI connectivity. `podman-compose config --quiet` checks configuration without pulling images.
- If another PostgreSQL server already uses port 5432, set `POSTGRES_PORT=5433` in `.env`. Compose and the Java development configuration use this port; container-to-container traffic still uses 5432.
- PostgreSQL data is retained across restarts. Do not remove its volume unless you intend to discard its records.
