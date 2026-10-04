# CORE — Centralized Operations & Routing Engine

### AI-Augmented Supply Chain Platform

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Next.js-15-000000?style=for-the-badge&logo=nextdotjs&logoColor=white" alt="Next.js">
  <img src="https://img.shields.io/badge/FastAPI-0.110+-009688?style=for-the-badge&logo=fastapi&logoColor=white" alt="FastAPI">
  <img src="https://img.shields.io/badge/LangChain-0.2+-1C3C3C?style=for-the-badge&logo=langchain&logoColor=white" alt="LangChain">
  <img src="https://img.shields.io/badge/PostgreSQL-15-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL">
  <img src="https://img.shields.io/badge/Apache%20Kafka-7.5-231F20?style=for-the-badge&logo=apachekafka&logoColor=white" alt="Kafka">
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker">
</p>

---

## Overview

**CORE** is a production-grade, AI-augmented microservices platform designed to modernize supply chain operations. It combines real-time inventory tracking, order lifecycle management, and an intelligent AI pipeline powered by Google Generative AI and LangChain — all orchestrated through a unified API gateway and event-driven architecture.

The platform is built on three pillars:

1. **Centralized Operations** — Unified gateway routing all traffic to domain-specific microservices
2. **Event-Driven Architecture** — Apache Kafka enabling real-time, asynchronous communication between services
3. **AI-Augmented Intelligence** — RAG (Retrieval-Augmented Generation) pipeline for supply chain insights and decision support

---

## Architecture

```
                         ┌─────────────────┐
                         │    Frontend      │
                         │   (Next.js)      │
                         │    :3000         │
                         └────────┬─────────┘
                                  │
                         ┌────────▼─────────┐
                         │   API Gateway    │
                         │ (Spring Cloud)   │
                         │    :8080         │
                         └──┬─────┬─────┬──┘
                            │     │     │
              ┌─────────────┤     │     ├─────────────┐
              │             │     │     │             │
    ┌─────────▼───┐  ┌─────▼─────▼─┐  ┌──────────▼──┐
    │  Inventory  │  │   Orders    │  │  AI Agent   │
    │  Service    │  │   Service   │  │  (FastAPI)  │
    │   :8081     │  │    :8082    │  │   :8090     │
    └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
           │                │                │
           │         ┌──────▼──────┐         │
           └────────►│   Kafka     │◄────────┘
                     │   :29092    │
                     └─────────────┘
           │                                 │
    ┌──────▼──────┐                   ┌──────▼──────┐
    │ PostgreSQL  │                   │  ChromaDB   │
    │   :5432     │                   │   :8000     │
    └─────────────┘                   └─────────────┘
```

---

## Project Structure

```
CORE/
├── frontend/                          Next.js (React + TypeScript)
│   ├── src/app/                       App Router pages & layouts
│   ├── public/                        Static assets
│   ├── Dockerfile                     Multi-stage production build
│   ├── package.json
│   └── tsconfig.json
│
├── gateway/                           Spring Cloud Gateway + Spring Security
│   ├── src/main/java/com/core/gateway/
│   │   ├── GatewayApplication.java    Application entry point
│   │   └── SecurityConfig.java        WebFlux security configuration
│   ├── src/main/resources/
│   │   └── application.yml            Route definitions & security config
│   ├── pom.xml                        Maven dependencies
│   └── Dockerfile
│
├── backend/
│   ├── inventory-service/             Inventory management microservice
│   │   ├── src/main/java/com/core/inventory/
│   │   │   ├── InventoryServiceApplication.java
│   │   │   ├── model/InventoryItem.java         JPA entity
│   │   │   ├── repository/InventoryRepository.java
│   │   │   ├── service/InventoryService.java    Business logic + Kafka events
│   │   │   └── controller/InventoryController.java  REST API
│   │   ├── src/main/resources/application.yml
│   │   ├── pom.xml
│   │   └── Dockerfile
│   │
│   └── orders-service/                Order lifecycle microservice
│       ├── src/main/java/com/core/orders/
│       │   ├── OrdersServiceApplication.java
│       │   ├── model/Order.java                 JPA entity with status workflow
│       │   ├── repository/OrderRepository.java
│       │   ├── service/OrderService.java        Business logic + Kafka events
│       │   └── controller/OrderController.java  REST API
│       ├── src/main/resources/application.yml
│       ├── pom.xml
│       └── Dockerfile
│
├── ai-agent/                          AI/ML pipeline service
│   ├── main.py                        FastAPI app with ChromaDB integration
│   ├── requirements.txt               Python dependencies
│   ├── .env.example                   Environment variable template
│   └── Dockerfile
│
├── infra/
│   └── init-db.sql                    PostgreSQL bootstrap (creates per-service DBs)
│
├── docker-compose.yml                 Full infrastructure & service orchestration
├── setup_core.sh                      Automated project bootstrap script
├── .env.example                       Root environment variable template
├── .gitignore
└── README.md
```

---

## Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Frontend** | Next.js 15, React, TypeScript | Server-side rendered UI with App Router |
| **API Gateway** | Spring Cloud Gateway, Spring Security | Centralized routing, authentication, rate limiting |
| **Inventory Service** | Spring Boot 3.2.5, Spring Data JPA | CRUD operations for inventory management |
| **Orders Service** | Spring Boot 3.2.5, Spring Data JPA | Order lifecycle with status workflow |
| **AI Agent** | FastAPI, LangChain, Google Generative AI | RAG pipeline for supply chain intelligence |
| **Database** | PostgreSQL 15 | Relational storage for inventory & orders |
| **Message Broker** | Apache Kafka (Confluent 7.5) | Event-driven async communication |
| **Vector Store** | ChromaDB | Embedding storage for AI retrieval |
| **Containerization** | Docker, Docker Compose | Service orchestration & deployment |

---

## Services & Ports

| Service | Port | Health Check |
|---------|------|-------------|
| Frontend (Next.js) | `3000` | `http://localhost:3000` |
| API Gateway | `8080` | `http://localhost:8080/actuator/health` |
| Inventory Service | `8081` | `http://localhost:8081/actuator/health` |
| Orders Service | `8082` | `http://localhost:8082/actuator/health` |
| AI Agent (FastAPI) | `8090` | `http://localhost:8090/health` |
| PostgreSQL | `5432` | — |
| Kafka | `29092` | — |
| Zookeeper | `2181` | — |
| ChromaDB | `8000` | `http://localhost:8000/api/v1/heartbeat` |

---

## API Routes (Gateway)

All requests are routed through the API Gateway at `:8080`:

| Route Pattern | Target Service | Port |
|--------------|----------------|------|
| `/api/inventory/**` | Inventory Service | 8081 |
| `/api/orders/**` | Orders Service | 8082 |
| `/api/ai/**` | AI Agent | 8090 |

### Inventory Service Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/inventory/inventory` | List all inventory items |
| `GET` | `/api/inventory/inventory/{id}` | Get item by ID |
| `POST` | `/api/inventory/inventory` | Create new item |
| `PUT` | `/api/inventory/inventory/{id}` | Update item |
| `DELETE` | `/api/inventory/inventory/{id}` | Delete item |

### Orders Service Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/orders/orders` | List all orders |
| `GET` | `/api/orders/orders/{id}` | Get order by ID |
| `POST` | `/api/orders/orders` | Create new order |
| `PATCH` | `/api/orders/orders/{id}/status?status=CONFIRMED` | Update order status |
| `DELETE` | `/api/orders/orders/{id}` | Cancel order |

### AI Agent Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/ai/health` | Health check |
| `POST` | `/api/ai/query` | RAG query (retrieval-augmented generation) |

---

## Prerequisites

- **Node.js** >= 18
- **Java** JDK 17+
- **Python** 3.10+
- **Docker** & Docker Compose
- **Maven** 3.9+ (or use included `mvnw` wrapper)

---

## Quick Start

### Option 1: Automated Setup

```bash
chmod +x setup_core.sh
./setup_core.sh
```

This script will automatically:
- Scaffold the Next.js frontend
- Download Spring Boot projects from Spring Initializr
- Create a Python virtual environment and install AI dependencies
- Generate all Dockerfiles, configs, and infrastructure files

### Option 2: Docker Compose (Full Stack)

```bash
docker compose up --build
```

This builds and starts all services including infrastructure.

### Option 3: Development Mode (Individual Services)

**Step 1 — Start infrastructure:**
```bash
docker compose up -d postgres zookeeper kafka chromadb
```

**Step 2 — Start backend services:**
```bash
cd gateway && ./mvnw spring-boot:run
cd backend/inventory-service && ./mvnw spring-boot:run
cd backend/orders-service && ./mvnw spring-boot:run
```

**Step 3 — Start AI agent:**
```bash
cd ai-agent
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --port 8090
```

**Step 4 — Start frontend:**
```bash
cd frontend
npm install
npm run dev
```

---

## Environment Variables

Copy `.env.example` to `.env` and configure:

```env
GOOGLE_API_KEY=your-google-generative-ai-key
POSTGRES_USER=core_admin
POSTGRES_PASSWORD=core_secret
```

For the AI agent, also configure `ai-agent/.env`:
```env
GOOGLE_API_KEY=your-google-generative-ai-key
CHROMA_HOST=localhost
CHROMA_PORT=8000
KAFKA_BOOTSTRAP_SERVERS=localhost:29092
```

---

## Event-Driven Architecture

Services communicate asynchronously via Apache Kafka topics:

| Topic | Producer | Event Types |
|-------|----------|-------------|
| `inventory-events` | Inventory Service | `CREATED`, `UPDATED`, `DELETED` |
| `order-events` | Orders Service | `CREATED`, `STATUS_CHANGED`, `CANCELLED` |

Events are published automatically when entities are created, updated, or deleted. Any service can subscribe to these topics to react to changes in real-time.

---

## Database Schema

PostgreSQL hosts three databases:

| Database | Service | Description |
|----------|---------|-------------|
| `core_main` | Gateway | Primary database (reserved) |
| `core_inventory` | Inventory Service | Inventory items with SKU, quantity, price |
| `core_orders` | Orders Service | Orders with status workflow (PENDING → CONFIRMED → SHIPPED → DELIVERED) |

Schema is auto-managed by Hibernate (`ddl-auto: update`).

---

## AI Pipeline

The AI Agent implements a **Retrieval-Augmented Generation (RAG)** architecture:

1. **Ingestion** — Documents and supply chain data are embedded and stored in ChromaDB
2. **Retrieval** — Incoming queries are matched against stored embeddings for relevant context
3. **Generation** — Google Generative AI produces answers grounded in retrieved context

Powered by:
- **LangChain** — Orchestration framework for LLM chains
- **Google Generative AI** — Large language model for response generation
- **ChromaDB** — Vector database for semantic similarity search

---

## License

This project is licensed under the MIT License.
