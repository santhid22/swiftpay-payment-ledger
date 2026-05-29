# SwiftPay Payment Ledger

Production-grade, real-time payment ledger engine built with Java 21, Spring Boot 3.2.5, and a Gradle multi-project architecture.

## Architecture Blueprint

SwiftPay uses event-driven microservices with strict consistency controls:

- **`transaction-gateway`**
  - Exposes payment intake API (`POST /v1/payments`, also `/api/v1/payments`)
  - Enforces distributed idempotency via Redis keys (`idempotency:<transactionId>`)
  - Persists initial transaction state as `PENDING` in PostgreSQL
  - Publishes `PaymentInitiatedEvent` to Kafka
- **`ledger-service`**
  - Consumes `PaymentInitiatedEvent`
  - Applies row-level pessimistic write locks (`SELECT ... FOR UPDATE`) on sender/receiver accounts
  - Executes atomic debit/credit update
  - Persists double-entry ledger trail (`DEBIT`, `CREDIT`)
  - Emits `PaymentResultEvent`
- **`shared`**
  - Shared DTOs, events, and domain exceptions
- **Infrastructure**
  - PostgreSQL for durable ledger storage
  - Kafka (KRaft) for event streaming
  - Redis for idempotency locking

## Non-Functional Controls Implemented

### Reliability and Resilience

- Kafka consumer retry in `ledger-service`:
  - `DefaultErrorHandler`
  - `FixedBackOff(2000ms, 3 retries)`
- Dead-letter path:
  - Failed messages routed to `payment-initiated.DLQ`

### Throughput and Concurrency (250 TPS target)

- HikariCP tuning in both services:
  - `maximum-pool-size: 50`
  - leak detection enabled (`leak-detection-threshold: 20000`)
- Kafka consumer tuning in `ledger-service`:
  - `max.poll.records: 500`
  - `fetch.min.bytes: 50000`
  - concurrent listeners set to `3` via `ConcurrentKafkaListenerContainerFactory`

### Observability

- Spring Actuator enabled on both services
- Health endpoints:
  - `/actuator/health`
  - mirrored `/health`
- Structured SLF4J tracing in gateway and ledger processors:
  - request intake
  - idempotency collisions
  - Kafka emit/consume
  - transactional commit/failure paths

### API Documentation

- OpenAPI 3 via Springdoc in both services
- Swagger UI: `/swagger-ui.html`
- API docs JSON: `/v3/api-docs`

## Local Bootstrap

### 1) Prerequisites

- Docker + Docker Compose
- Java 21
- Optional: `k6`, `bash`, `tcpdump` (or Docker-based tap via script)

### 2) Build

```bash
./gradlew clean build
```

### 3) Start Infrastructure + Services

```bash
docker compose up --build -d
```

### 4) Verify Health

```bash
curl http://localhost:8080/health
curl http://localhost:8081/health
```

### 5) Open API Docs

- Gateway: `http://localhost:8080/swagger-ui.html`
- Ledger: `http://localhost:8081/swagger-ui.html`

## Payment API Example

### Request

```bash
curl -X POST "http://localhost:8080/v1/payments" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId":"tx-demo-00001",
    "senderAccountId":"acct-sender-1",
    "receiverAccountId":"acct-receiver-1",
    "amount":150.75,
    "currency":"USD"
  }'
```

Expected response code: `202 Accepted`

## Integration Testing (Testcontainers)

Integration suites run with real Dockerized dependencies:

- `transaction-gateway` integration test validates:
  - idempotency lock behavior
  - persistence as `PENDING`
  - Kafka event emission
- `ledger-service` integration test validates:
  - event consumption
  - locked account balance mutation
  - double-entry trail creation

Run:

```bash
./gradlew :transaction-gateway:test :ledger-service:test
```

## K6 Load Testing (250 TPS to 1M requests)

Load script location:

- `load-testing/k6-load-test.js`

Default profile:

- constant arrival rate: `250 TPS`
- total requests: `1,000,000`
- endpoint: `POST /v1/payments`
- unique `transactionId` generated per request to avoid idempotency rejections

Run:

```bash
k6 run load-testing/k6-load-test.js
```

Optional overrides:

```bash
BASE_URL=http://localhost:8080 TARGET_TPS=250 TOTAL_REQUESTS=1000000 k6 run load-testing/k6-load-test.js
```

## PCAP Network Trace Capture

Script location:

- `capture-traffic.sh`

Behavior:

- attaches a lightweight Docker tap container to the compose network
- captures traffic on ports:
  - `9092` Kafka
  - `5432` PostgreSQL
  - `6379` Redis
- writes trace file to project root as:
  - `network_traffic.pcap`

Run:

```bash
chmod +x capture-traffic.sh
./capture-traffic.sh
```

Stop with `Ctrl+C` after load test completes.

## Validation Mapping to 250 TPS Production Objective

- **Sustained ingestion target:** enforced by K6 constant-arrival-rate profile at 250 TPS
- **Database safety under concurrency:** pessimistic locks plus larger Hikari pool
- **Messaging stability:** bounded retries + DLQ routing prevent consumer deadlock
- **Operational visibility:** actuator + structured logs + OpenAPI contracts
- **Transport-level evidence:** PCAP trace allows protocol-level inspection during stress

## CI/CD

GitHub Actions workflow:

- `.github/workflows/ci-cd.yml`

Pipeline includes:

- JDK 21 setup with Gradle cache
- compile
- test execution
- Docker dry-builds for both services
