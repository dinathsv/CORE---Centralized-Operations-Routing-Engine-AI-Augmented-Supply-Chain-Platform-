#!/usr/bin/env bash
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m'

banner() {
  echo ""
  echo -e "${CYAN}${BOLD}==============================================================${NC}"
  echo -e "${CYAN}${BOLD}  $1${NC}"
  echo -e "${CYAN}${BOLD}==============================================================${NC}"
  echo ""
}

step() { echo -e "  ${GREEN}[ok]${NC} $1"; }
warn() { echo -e "  ${YELLOW}[skip]${NC} $1"; }
fail() { echo -e "  ${RED}[FAIL] $1${NC}"; exit 1; }

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

banner "Pre-flight checks"

command -v node    >/dev/null 2>&1 || fail "Node.js is not installed (need >= 18)"
command -v npm     >/dev/null 2>&1 || fail "npm is not installed"
command -v npx     >/dev/null 2>&1 || fail "npx is not installed"
command -v java    >/dev/null 2>&1 || fail "Java (JDK 17+) is not installed"
command -v python3 >/dev/null 2>&1 || fail "Python 3 is not installed"
command -v curl    >/dev/null 2>&1 || fail "curl is not installed"
command -v unzip   >/dev/null 2>&1 || fail "unzip is not installed"
command -v docker  >/dev/null 2>&1 || warn "Docker not found - docker-compose won't work"

step "All prerequisites satisfied"

banner "1 / 5  -  Frontend (Next.js)"

if [ -d "$ROOT_DIR/frontend" ] && [ -f "$ROOT_DIR/frontend/package.json" ]; then
  warn "frontend/ already exists - skipping"
else
  npx -y create-next-app@latest "$ROOT_DIR/frontend" \
    --typescript \
    --app \
    --eslint \
    --src-dir \
    --use-npm \
    --import-alias "@/*" \
    --disable-git \
    --yes
  step "Next.js app scaffolded in frontend/"
fi

cat > "$ROOT_DIR/frontend/Dockerfile" << 'DOCKERFILE'
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
COPY --from=builder /app/.next ./.next
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package.json ./
COPY --from=builder /app/public ./public
EXPOSE 3000
CMD ["npm", "start"]
DOCKERFILE
step "frontend/Dockerfile created"

banner "2 / 5  -  API Gateway (Spring Cloud Gateway)"

GATEWAY_DIR="$ROOT_DIR/gateway"

if [ -d "$GATEWAY_DIR/src" ]; then
  warn "gateway/ already initialised - skipping download"
else
  mkdir -p "$GATEWAY_DIR"
  curl -sS "https://start.spring.io/starter.zip" \
    -d type=maven-project \
    -d language=java \
    -d bootVersion=3.2.5 \
    -d baseDir=gateway \
    -d groupId=com.core \
    -d artifactId=gateway \
    -d name=gateway \
    -d "description=CORE API Gateway" \
    -d packageName=com.core.gateway \
    -d javaVersion=17 \
    -d dependencies=cloud-gateway,security,actuator,lombok \
    -o /tmp/core-gateway.zip

  unzip -qo /tmp/core-gateway.zip -d "$ROOT_DIR"
  rm -f /tmp/core-gateway.zip
  step "Spring Cloud Gateway project generated"
fi

mkdir -p "$GATEWAY_DIR/src/main/resources"
cat > "$GATEWAY_DIR/src/main/resources/application.yml" << 'APPYML'
server:
  port: 8080

spring:
  application:
    name: core-gateway
  cloud:
    gateway:
      routes:
        - id: inventory-service
          uri: http://inventory-service:8081
          predicates:
            - Path=/api/inventory/**
          filters:
            - StripPrefix=1
        - id: orders-service
          uri: http://orders-service:8082
          predicates:
            - Path=/api/orders/**
          filters:
            - StripPrefix=1
        - id: ai-agent
          uri: http://ai-agent:8090
          predicates:
            - Path=/api/ai/**
          filters:
            - StripPrefix=1
  security:
    user:
      name: admin
      password: admin

management:
  endpoints:
    web:
      exposure:
        include: health,info,gateway
APPYML
step "gateway/application.yml configured"

cat > "$GATEWAY_DIR/Dockerfile" << 'DOCKERFILE'
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./mvnw -q clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
DOCKERFILE
step "gateway/Dockerfile created"

banner "3 / 5  -  Backend Microservices"

BACKEND_DIR="$ROOT_DIR/backend"
mkdir -p "$BACKEND_DIR"

INV_DIR="$BACKEND_DIR/inventory-service"

if [ -d "$INV_DIR/src" ]; then
  warn "backend/inventory-service/ already initialised - skipping"
else
  curl -sS "https://start.spring.io/starter.zip" \
    -d type=maven-project \
    -d language=java \
    -d bootVersion=3.2.5 \
    -d baseDir=inventory-service \
    -d groupId=com.core \
    -d artifactId=inventory-service \
    -d name=inventory-service \
    -d "description=CORE Inventory Service" \
    -d packageName=com.core.inventory \
    -d javaVersion=17 \
    -d dependencies=web,data-jpa,postgresql,kafka,actuator,lombok,validation \
    -o /tmp/core-inventory.zip

  unzip -qo /tmp/core-inventory.zip -d "$BACKEND_DIR"
  rm -f /tmp/core-inventory.zip
  step "InventoryService scaffolded"
fi

mkdir -p "$INV_DIR/src/main/resources"
cat > "$INV_DIR/src/main/resources/application.yml" << 'APPYML'
server:
  port: 8081

spring:
  application:
    name: inventory-service
  datasource:
    url: jdbc:postgresql://localhost:5432/core_inventory
    username: core_admin
    password: core_secret
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  kafka:
    bootstrap-servers: localhost:29092
    consumer:
      group-id: inventory-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

management:
  endpoints:
    web:
      exposure:
        include: health,info
APPYML

cat > "$INV_DIR/Dockerfile" << 'DOCKERFILE'
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./mvnw -q clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
DOCKERFILE
step "inventory-service/Dockerfile created"

ORD_DIR="$BACKEND_DIR/orders-service"

if [ -d "$ORD_DIR/src" ]; then
  warn "backend/orders-service/ already initialised - skipping"
else
  curl -sS "https://start.spring.io/starter.zip" \
    -d type=maven-project \
    -d language=java \
    -d bootVersion=3.2.5 \
    -d baseDir=orders-service \
    -d groupId=com.core \
    -d artifactId=orders-service \
    -d name=orders-service \
    -d "description=CORE Orders Service" \
    -d packageName=com.core.orders \
    -d javaVersion=17 \
    -d dependencies=web,data-jpa,postgresql,kafka,actuator,lombok,validation \
    -o /tmp/core-orders.zip

  unzip -qo /tmp/core-orders.zip -d "$BACKEND_DIR"
  rm -f /tmp/core-orders.zip
  step "OrdersService scaffolded"
fi

mkdir -p "$ORD_DIR/src/main/resources"
cat > "$ORD_DIR/src/main/resources/application.yml" << 'APPYML'
server:
  port: 8082

spring:
  application:
    name: orders-service
  datasource:
    url: jdbc:postgresql://localhost:5432/core_orders
    username: core_admin
    password: core_secret
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  kafka:
    bootstrap-servers: localhost:29092
    consumer:
      group-id: orders-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

management:
  endpoints:
    web:
      exposure:
        include: health,info
APPYML

cat > "$ORD_DIR/Dockerfile" << 'DOCKERFILE'
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./mvnw -q clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
DOCKERFILE
step "orders-service/Dockerfile created"

banner "4 / 5  -  AI Pipeline (FastAPI + LangChain)"

AI_DIR="$ROOT_DIR/ai-agent"
mkdir -p "$AI_DIR"

cat > "$AI_DIR/requirements.txt" << 'REQUIREMENTS'
fastapi>=0.110.0,<1.0.0
uvicorn[standard]>=0.29.0,<1.0.0
langchain>=0.2.0,<1.0.0
langchain-google-genai>=1.0.0,<2.0.0
chromadb>=0.5.0,<1.0.0
pydantic>=2.0.0,<3.0.0
python-dotenv>=1.0.0
httpx>=0.27.0,<1.0.0
confluent-kafka>=2.3.0,<3.0.0
REQUIREMENTS
step "ai-agent/requirements.txt generated"

if [ -d "$AI_DIR/venv" ]; then
  warn "ai-agent/venv already exists - skipping virtualenv creation"
else
  python3 -m venv "$AI_DIR/venv"
  step "Virtual environment created"
fi

source "$AI_DIR/venv/bin/activate"
pip install --quiet --upgrade pip
pip install --quiet -r "$AI_DIR/requirements.txt"
deactivate
step "Python dependencies installed"

cat > "$AI_DIR/main.py" << 'PYFILE'
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
    if chroma_client is None:
        raise HTTPException(status_code=503, detail="ChromaDB not connected")
    return QueryResponse(
        answer=f"Received query: {request.query}",
        sources=[],
    )
PYFILE
step "ai-agent/main.py scaffolded"

cat > "$AI_DIR/.env.example" << 'ENVFILE'
GOOGLE_API_KEY=your-google-api-key
CHROMA_HOST=localhost
CHROMA_PORT=8000
KAFKA_BOOTSTRAP_SERVERS=localhost:29092
ENVFILE
step "ai-agent/.env.example created"

cat > "$AI_DIR/Dockerfile" << 'DOCKERFILE'
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 8090
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8090"]
DOCKERFILE
step "ai-agent/Dockerfile created"

banner "5 / 5  -  Infrastructure and Shared Config"

mkdir -p "$ROOT_DIR/infra"

cat > "$ROOT_DIR/infra/init-db.sql" << 'SQL'
CREATE DATABASE core_inventory;
CREATE DATABASE core_orders;
GRANT ALL PRIVILEGES ON DATABASE core_inventory TO core_admin;
GRANT ALL PRIVILEGES ON DATABASE core_orders    TO core_admin;
SQL
step "infra/init-db.sql created"

cat > "$ROOT_DIR/.env.example" << 'ENVFILE'
GOOGLE_API_KEY=your-google-api-key
POSTGRES_USER=core_admin
POSTGRES_PASSWORD=core_secret
ENVFILE
step ".env.example created"

cat > "$ROOT_DIR/.gitignore" << 'GITIGNORE'
.DS_Store
Thumbs.db
.idea/
.vscode/
*.iml
target/
*.class
*.jar
*.war
node_modules/
.next/
out/
__pycache__/
*.pyc
venv/
.venv/
docker-compose.override.yml
.env
*.env.local
GITIGNORE
step ".gitignore created"

banner "CORE project initialisation complete!"

echo -e "  ${BOLD}Project structure:${NC}"
echo ""
echo "  CORE/"
echo "  |-- frontend/              (Next.js)"
echo "  |-- gateway/               (Spring Cloud Gateway)"
echo "  |-- backend/"
echo "  |   |-- inventory-service/ (Spring Boot)"
echo "  |   +-- orders-service/    (Spring Boot)"
echo "  |-- ai-agent/              (FastAPI + LangChain)"
echo "  |-- infra/"
echo "  |   +-- init-db.sql"
echo "  |-- docker-compose.yml"
echo "  |-- setup_core.sh"
echo "  +-- .gitignore"
echo ""
echo -e "  ${BOLD}Quick start:${NC}"
echo -e "    1. Start infrastructure:  ${CYAN}docker compose up -d postgres zookeeper kafka chromadb${NC}"
echo -e "    2. Start AI agent:        ${CYAN}cd ai-agent && source venv/bin/activate && uvicorn main:app --reload --port 8090${NC}"
echo -e "    3. Start frontend:        ${CYAN}cd frontend && npm run dev${NC}"
echo -e "    4. Build all services:    ${CYAN}docker compose up --build${NC}"
echo ""
