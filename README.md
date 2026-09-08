# Junes — Online Video Game Store (Backend)

Junes is a prototype e-commerce backend for an online video game store, built as a portfolio project to demonstrate production-style backend engineering patterns: event-driven order/payment processing, a transactional outbox, dual-database persistence, third-party payment integration, and a full local observability stack.

It is a **Spring Boot 3.3 / Java 17** REST API. The frontend lives in a sibling repo ([`junes-fe`](../junes-fe)), and product recommendations are served by a companion Python microservice ([`recommendation-engine`](../recommendation-engine)) called over HTTP.

> This is a dummy store — no real payments, emails, or user data are involved. Stripe runs in test mode, and all "production" credentials in this repo are local dev values.

## Table of contents

- [Tech stack](#tech-stack)
- [External integrations](#external-integrations)
- [Architecture at a glance](#architecture-at-a-glance)
- [Workflows](#workflows)
  - [User registration & email verification](#user-registration--email-verification)
  - [Address: add / edit / delete](#address-add--edit--delete)
  - [Payment method: add / edit / delete](#payment-method-add--edit--delete)
  - [Order placement](#order-placement)
- [Running the app](#running-the-app)
- [Accessing supporting services](#accessing-supporting-services)
- [Testing & code quality](#testing--code-quality)
- [Project structure](#project-structure)

## Tech stack

| Category | Technology |
|---|---|
| Language / runtime | Java 17 |
| Framework | Spring Boot 3.3 (Web MVC + WebFlux, Security, Validation, AOP, Actuator) |
| Relational data | PostgreSQL 15 + Spring Data JPA (users, addresses, transactions, payment methods, outbox/idempotency) |
| Document data | MongoDB 6 + Spring Data MongoDB (product catalog) |
| Caching / rate limiting | Redis 7 (cart cache, recommendation cache, order-history cache, Bucket4j token buckets) |
| Messaging (events) | Apache Kafka (order/payment/inventory/Stripe-sync events, transactional outbox pattern) |
| Messaging (email) | RabbitMQ (async email dispatch with a dead-letter queue) |
| Auth | Stateless JWT (`jjwt`), Spring Security filter chain |
| API docs | springdoc-openapi / Swagger UI |
| Mapping | MapStruct (entity ↔ DTO) |
| Resilience | Resilience4j (circuit breaker + retry around the recommendation engine call) |
| Scheduling / coordination | Spring `@Scheduled` + ShedLock (Postgres-backed distributed locks) for outbox relay & housekeeping jobs |
| Templating | Thymeleaf (transactional email templates) |
| Logging | Logback + Logstash encoder → ELK (Elasticsearch, Logstash, Kibana) |
| Metrics | Micrometer → Prometheus → Grafana, with Alertmanager |
| CI/CD | Jenkins (declarative pipeline, custom agent + controller images) |
| Containers | Docker / Docker Compose |

## External integrations

| Service | Used for | Called from |
|---|---|---|
| **Stripe API** | Customer/payment-method management, SetupIntents (card tokenization), charges | `service/payment/StripeService`, `StripePaymentGatewayService`, `service/payment/SavedItemsService` |
| **Mailgun** | Transactional email delivery (verification, password reset, order updates) | `service/email/EmailConsumerService`, consumed off `email.queue` |
| **MaxMind GeoIP2** | IP → geolocation enrichment for request/device context | `service/GeoLocationService` |
| **Recommendation engine** (sibling Python service, `recommendation-api`) | Personalized product recommendations, over WebFlux `WebClient` behind a Resilience4j circuit breaker + retry | `service/product/RecommendationService`, `config/RecommenderSystemClientConfig` |

All four are called synchronously except Stripe, whose async side effects (customer email sync, payment-method detach) also flow back in over Kafka topics `stripe-sync-events`, `stripe-detach-payment-method-events`, and `stripe-payment-method-sync-events`.

## Architecture at a glance

```mermaid
flowchart LR
    Client["Client (junes-fe)"] -->|REST / JWT| API[Junes Spring Boot API]

    API --> PG[(PostgreSQL<br/>users, addresses, transactions,<br/>payment methods, outbox)]
    API --> Mongo[(MongoDB<br/>product catalog)]
    API --> Redis[(Redis<br/>cart / recommendation /<br/>order-history cache, rate limits)]

    API -->|publish| RMQ[[RabbitMQ<br/>email.queue]]
    RMQ --> EmailConsumer[EmailConsumerService] --> Mailgun[(Mailgun)]

    API -->|outbox write| PG
    Relay["OutboxRelay<br/>@Scheduled, ShedLock"] -->|poll| PG
    Relay -->|publish| Kafka[[Kafka<br/>order-events / inventory-events /<br/>stripe-* events]]
    Kafka --> Consumers["Kafka @KafkaListener consumers<br/>Payment, OrderFinalisation, Stripe sync/detach"]
    Consumers --> PG

    API -->|charge / setup intent| Stripe[(Stripe API)]
    API -->|geo lookup| MaxMind[(MaxMind GeoIP2)]
    API -->|WebClient, circuit breaker| Recs[recommendation-engine<br/>Python service]
    Recs --> Mongo
    Recs --> Redis

    API -.->|JSON logs| Logstash[[Logstash]] --> ES[(Elasticsearch)] --> Kibana[Kibana]
    API -.->|/actuator/prometheus| Prometheus --> Grafana
```

## Workflows

All sequence diagrams below reflect the actual outbox/idempotency pattern used in the code, not a simplified version of it.

### User registration & email verification

`POST /junes/api/v1/auth/add-user` → `AuthService.addUser`

```mermaid
sequenceDiagram
    actor Client
    participant Auth as AuthController
    participant Svc as AuthService
    participant PG as PostgreSQL
    participant RMQ as RabbitMQ (email.queue)
    participant EmailConsumer as EmailConsumerService
    participant Mailgun

    Client->>Auth: POST /auth/add-user {email, password}
    Auth->>Svc: addUser(email, password)
    Svc->>Svc: validate email & password strength
    Svc->>PG: delete stale unverified records for email
    Svc->>PG: save User (isEmailVerified = false)
    Svc->>PG: create EmailVerificationToken
    Svc->>RMQ: publish verification email message
    Auth-->>Client: 200 OK "User added with unverified email"

    RMQ-->>EmailConsumer: consume message
    EmailConsumer->>Mailgun: send verification email (Thymeleaf template)

    Client->>Auth: GET /auth/verify?token=...
    Auth->>Svc: verify token
    Svc->>PG: mark user isEmailVerified = true, activate cart/wishlist
    Auth-->>Client: 200 OK
```

### Address: add / edit / delete

`SavedItemsController` (`/junes/api/v1/saved-items/address*`) → `SavedItemsService`, backed directly by PostgreSQL — no outbox involved, since addresses have no external system to stay in sync with.

```mermaid
sequenceDiagram
    actor Client
    participant SI as SavedItemsController
    participant Svc as SavedItemsService
    participant PG as PostgreSQL (Address)

    Client->>SI: POST /saved-items/address {addressDTO}
    SI->>Svc: addAddress(userID, addressDTO)
    Svc->>PG: validate limit & duplicates, insert row
    SI-->>Client: 200 OK "Address successfully added"

    Client->>SI: PUT /saved-items/address/{addressID}
    SI->>Svc: editAddress(userID, addressID, addressDTO)
    Svc->>PG: validate ownership, update row
    SI-->>Client: 200 OK "Address successfully edited"

    Client->>SI: DELETE /saved-items/address/{addressID}
    SI->>Svc: deleteAddress(userID, addressID)
    Svc->>PG: soft delete (isActive = false)
    SI-->>Client: 200 OK "Address successfully deleted"
```

### Payment method: add / edit / delete

Card details never touch the Junes backend: the frontend collects them via Stripe Elements against a `SetupIntent`, and only the resulting Stripe `payment_method` ID is sent to us. Add/edit/delete are idempotent (`Idempotency-Key` header) and delete fans out to Stripe asynchronously via the outbox so the UI isn't blocked on a Stripe round trip.

```mermaid
sequenceDiagram
    actor Client
    participant SI as SavedItemsController
    participant Svc as SavedItemsService
    participant Stripe as Stripe API
    participant PG as PostgreSQL (PaymentMethod, Outbox)
    participant Relay as OutboxRelay
    participant Kafka as Kafka (stripe-detach-payment-method-events)
    participant Detach as PaymentMethodDetachConsumer

    Client->>SI: POST /saved-items/payment-method/setup-intent
    SI->>Stripe: create SetupIntent for customer
    Stripe-->>Client: client secret (Stripe.js collects & tokenizes card)

    Client->>SI: POST /saved-items/payment-method {stripePaymentMethodID} [Idempotency-Key]
    SI->>Svc: addPaymentMethod(...)
    Svc->>Stripe: retrieve & validate PaymentMethod belongs to customer
    Svc->>PG: save PaymentMethod row
    SI-->>Client: 200 OK "Payment method successfully added"

    Client->>SI: PUT /saved-items/payment-method/{id} [Idempotency-Key]
    SI->>Svc: editPaymentMethod(...)
    Svc->>Svc: no-op if unchanged, dedupe via Idempotency-Key
    Svc->>PG: update cardholder name / expiry
    SI-->>Client: 200 OK "Payment method successfully edited"

    Client->>SI: DELETE /saved-items/payment-method/{id} [Idempotency-Key]
    SI->>Svc: deletePaymentMethod(...)
    Svc->>PG: soft delete row (isActive = false) — disappears from UI immediately
    Svc->>PG: write StripePaymentMethodDetachEvent to outbox (same transaction)
    SI-->>Client: 200 OK "Payment method successfully deleted"

    Relay->>PG: poll unpublished outbox events (every 500ms, ShedLock-guarded)
    Relay->>Kafka: publish detach event
    Kafka-->>Detach: consume
    Detach->>Stripe: detach PaymentMethod from customer
    Detach->>PG: mark ProcessedEvent (consumer-side idempotency)
```

### Order placement

`POST /junes/api/v1/order/place` → the flow spans a synchronous request/response plus two asynchronous Kafka consumers, tied together by the transactional outbox so the DB write and the Kafka publish never diverge.

```mermaid
sequenceDiagram
    actor Client
    participant Order as OrderController
    participant Creation as OrderCreationService
    participant PG as PostgreSQL (Transaction, Outbox)
    participant Relay as OutboxRelay
    participant Kafka as Kafka (order-events)
    participant PayCons as PaymentEventConsumer
    participant Stripe as Stripe API
    participant FinalCons as OrderFinalisationConsumer
    participant EmailQ as RabbitMQ (email.queue)

    Client->>Order: POST /order/place {cart, address, paymentMethod} [Idempotency-Key]
    Order->>Creation: createPendingOrder(...)
    Creation->>PG: insert Transaction (status = PAYMENT_PENDING)
    Creation->>PG: write OrderCreatedEvent to outbox (same DB transaction)
    Order-->>Client: 200 OK {orderNumber}

    Relay->>PG: poll unpublished outbox events (every 500ms, ShedLock-guarded)
    Relay->>Kafka: publish OrderCreatedEvent

    Kafka-->>PayCons: consume OrderCreatedEvent
    PayCons->>PG: check ProcessedEvent (consumer-side idempotency)
    PayCons->>Stripe: charge payment method (Stripe idempotency key = order number)
    alt payment succeeds
        PayCons->>PG: write PaymentSucceededEvent to outbox
    else payment fails
        PayCons->>PG: write PaymentFailedEvent to outbox
    end

    Relay->>Kafka: publish Payment(Succeeded|Failed)Event

    Kafka-->>FinalCons: consume Payment(Succeeded|Failed)Event
    alt succeeded
        FinalCons->>PG: Transaction.status = AWAITING_SHIPMENT
    else failed
        FinalCons->>PG: Transaction.status = PAYMENT_FAILED
    end
    FinalCons->>EmailQ: publish order confirmation / failure email
```

## Running the app

### Prerequisites

- Java 17
- Docker + Docker Compose
- A sibling checkout of [`recommendation-engine`](../recommendation-engine) at `../recommendation-engine` (its Dockerfile is built as part of `docker compose up`)

### Steps

```bash
# 1. Start all infra dependencies (Postgres, MongoDB, Redis, RabbitMQ, Kafka,
#    the recommendation engine, and the ELK / monitoring stack)
docker compose up

# 2. Run the app itself (docker-compose.yml does not start the app — it connects
#    to the containers above via localhost)
./mvnw spring-boot:run

# 3. Stop infra when done
docker compose down
```

Other useful Maven commands:

```bash
./mvnw clean install                     # full build
./mvnw clean test                        # unit tests
./mvnw test -Dtest=ClassName             # single test class
./mvnw test -Dtest=ClassName#methodName  # single test method
```

Once running, the API is at `localhost:8080`, and interactive API docs are at:

```
localhost:8080/swagger-ui/index.html   # Swagger UI
localhost:8080/api-docs                # raw OpenAPI JSON
```

## Accessing supporting services

| Service | URL / command | Notes |
|---|---|---|
| **PgAdmin** | `localhost:5050` | Login with the credentials in `docker-compose.yml`. Manage the Postgres tables (users, addresses, transactions, payment methods, outbox). |
| **Mongo Compass** | run `mongodb-compass` | Manage product catalog documents. Connect using the Mongo credentials in `docker-compose.yml`. |
| **RabbitMQ management UI** | `localhost:15672` | Login with the credentials in `docker-compose.yml`. Inspect `email.queue`, `email.exchange`, and the dead-letter queue. |
| **Kafka** | broker at `localhost:9092` | No GUI is bundled; inspect topics with the CLI, e.g. `docker compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list` or `kafka-console-consumer` to tail `order-events`. |
| **Redis** | `localhost:6379` | No GUI is bundled; use `redis-cli` or a tool like RedisInsight if you have one installed. |
| **Elasticsearch** | `localhost:9200` | No auth, local dev only. |
| **Kibana** | `localhost:5601` | Pre-provisioned `junes-logs-*` index pattern, plus two saved searches under **Discover > Open**: `Logs by Correlation ID` (filter with `correlationId: "<value>"` to trace one request end-to-end) and `Error & Warn Logs (All Services)`. App logs are shipped automatically via Logstash on `localhost:5044` — no manual setup. If Elasticsearch fails to start with a `vm.max_map_count` error (Linux only): `sudo sysctl -w vm.max_map_count=262144`. |
| **Prometheus** | `localhost:9090` | Scrapes `/actuator/prometheus` on `localhost:9404` every 15s — **the app must be running locally** (`./mvnw spring-boot:run`), since compose does not start the app itself. Check **Status > Targets** for the `junes-app` job. |
| **Grafana** | `localhost:3000` | Login `admin` / `admin` (local dev only). Dashboards **API Latency & Error Rate**, **JVM Health**, and **Kafka Consumer Lag** are provisioned automatically under the **Junes** folder. |
| **Alertmanager** | `localhost:9093` | Paired with Prometheus alerting rules under `monitoring/`. |
| **Jenkins** | `localhost:8090/jenkins` | CI pipeline defined in `Jenkinsfile`; builds against `docker-compose.app.yml`. |

## Testing & code quality

- Unit tests run with `./mvnw clean test`.

## Project structure

```
config/        Spring @Configuration classes (security, Kafka, Mongo, Redis, RabbitMQ, rate limiting, Stripe, etc.)
controller/    REST controllers
service/       business logic, split by domain: auth/, payment/, product/, cart/, email/, order/, consumer/, outbox/, cache/
model/         JPA @Entity and MongoDB @Document classes
repository/    Spring Data repositories (jpa/ and mongodb/)
dto/           request/, response/, event/, recommender/ subpackages
mapper/        MapStruct entity <-> DTO mappers
exception/     custom exceptions, handled centrally in config/GlobalExceptionHandler
util/          validators, JWT, Kafka event parsing, ID generation, housekeeping tasks
constant/      shared string/enum constants (e.g. KafkaConstants)
annotation/    custom annotations
```

## License

Licensed under the [Apache License 2.0](LICENSE).
