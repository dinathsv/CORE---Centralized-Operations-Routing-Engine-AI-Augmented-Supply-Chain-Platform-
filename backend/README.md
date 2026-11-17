# Inventory and orders services

Both services require Java 17+, PostgreSQL, and Kafka. Use the root Podman Compose
stack in `compose.yml`, or start each service with `./mvnw spring-boot:run` after starting the
infrastructure. Host defaults use PostgreSQL on port 5432 and Kafka on 29092.
`SPRING_DATASOURCE_*` and `SPRING_KAFKA_BOOTSTRAP_SERVERS` override those defaults.

Run isolated tests with `./mvnw test`. Tests use H2 and disable the outbox poller;
no running database or broker is needed. Package an executable JAR with
`./mvnw package`.

Inventory CRUD is available at `/inventory` on port 8081. Each item contains
`id`, `sku`, `name`, `description`, `quantity`, and `price`. A SKU is unique and
trimmed, quantity is a nonnegative integer, and price is a nonnegative decimal
with at most two decimal places. IDs belong to the server.

Order CRUD is available at `/orders` on port 8082. New orders accept
`customerName`, `sku`, and a positive integer `quantity`; the server chooses the
ID, UTC `createdAt`, and initial `PENDING` status. Advance an order with
`PATCH /orders/{id}/status?status=CONFIRMED`. The allowed workflow is
`PENDING -> CONFIRMED -> SHIPPED -> DELIVERED`. Pending or confirmed orders may
be cancelled through `DELETE /orders/{id}` or a status change to `CANCELLED`.
Cancellation retains the order for history. Repeating the current status or
cancelling an already cancelled order succeeds without generating another event.
Invalid transitions return 409, absent records return 404, and malformed or
invalid requests return 400 as `application/problem+json`.

Orders reference SKUs without reserving, decrementing, or validating inventory.
Stock changes and order changes are independent operations. Inventory and orders
use separate databases; this version does not implement a stock reservation saga.

## Durable event delivery

Every mutation writes a JSON event into its own database's `outbox_events` table
in the same transaction as the domain change. A poller publishes committed rows
to `inventory-events` or `order-events` and removes each row only after the broker
acknowledges it. A broker outage does not undo a successful HTTP mutation; queued
events survive process restarts and retry when the broker recovers. Failed sends
are logged. Setting `CORE_OUTBOX_ENABLED=false` pauses publication.

Events include `eventId`, `type`, `entityType`, `entityId`, `occurredAt`, and a
`data` snapshot. They use the entity ID as the Kafka key. Delivery is at least
once: a crash after Kafka acknowledges an event but before its outbox row is
deleted may cause a duplicate. Consumers must deduplicate by `eventId`.

Run one instance of each service with the current poller. Multiple pollers do not
coordinate ownership of rows and can publish duplicates or reorder events. There
is no global ordering guarantee across services, and a consumer should not infer
cross-service transaction boundaries. Scaling publication requires coordinated
row claiming or a CDC-based outbox dispatcher.

JPA optimistic locking rejects overlapping database writes with 409. The internal
version is hidden from API clients; stale browser edits made sequentially are not
conditional updates. Add an ETag / If-Match contract before requiring that form of
edit conflict detection. Schema updates currently use Hibernate `ddl-auto=update`;
versioned migrations are needed before production schema upgrades.
