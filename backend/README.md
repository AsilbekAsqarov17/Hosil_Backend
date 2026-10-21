# Hosil Platform Backend

**Architecture:** Java 21 + Spring Boot 3.3.4 Modular Monolith  
**Persistence:** PostgreSQL 16 with Spring Data JPA & Flyway Migrations  
**Admin Back-Office:** Server-Rendered Thymeleaf + HTMX (`ADR-002`)  
**Architecture Verification:** Spring Modulith 1.2.3

---

## 1. Domain Modules & Encapsulation

The backend is structured as a modular monolith adhering strictly to [`docs/02_architecture.md`](../docs/02_architecture.md) and [`docs/04_domain_model.md`](../docs/04_domain_model.md). Boundaries are automatically verified during test execution using **Spring Modulith**:

- `uz.hosil.platform.identity`: Users, user profiles, multi-role RBAC, phone OTP challenges, refresh tokens, and public profiles.
- `uz.hosil.platform.farm`: Farms, field parcels, crop catalog, seasonal crop cycles, daily field activities, and harvest batches.
- `uz.hosil.platform.marketplace`: Standard units of measure catalog, hierarchical categories, wholesale harvest and input listings, and explicit harvest allocation ledger.
- `uz.hosil.platform.logistics`: Shipment requests, carrier quotes, assignments, and cryptographic/photo proofs of delivery.
- `uz.hosil.platform.reputation`: Decoupled two-sided reviews, double-blind publishing, and dispute mediation.
- `uz.hosil.platform.admin`: Operational management, verification queues, moderation, and mandatory audit log recording (`ADM-005`).
- `uz.hosil.platform.common`: Global exception handling (`ErrorEnvelope`), security filter chains, and replaceable SMS adapter ports.

---

## 2. Database Migrations (Flyway)

Migrations are located in `src/main/resources/db/migration/` and execute in strict sequential order:

1. `V1__init_extensions_and_identity.sql`:
   - Enables `uuid-ossp` extension for RFC 4122 UUID v4 generation.
   - Tables: `users`, `user_profiles`, `user_roles`, `otp_challenges`, `refresh_tokens`, `seller_profiles`, `carrier_profiles`.
2. `V2__farm_core_schema.sql`:
   - Tables: `farms`, `farm_memberships`, `fields`, `crop_catalog`, `crop_cycles`, `farm_activities`, `harvest_batches`.
   - Single-row CHECK constraints enforce `available_quantity <= total_quantity`.
   - *Cross-row quantity conservation:* Aggregate allocation conservation (`sum(allocations) + available_quantity == total_quantity`) requires transactional enforcement with pessimistic row locking in Java services.
3. `V3__marketplace_bridge_schema.sql`:
   - Tables: `units_of_measure`, `categories`, `listings`, `harvest_allocations`.
   - Seeds baseline measurement units (`KG`, `TON`, `PIECE`, `LITER`, `BAG_50KG`) and root categories (`HARVEST`, `FERTILIZER`, `SEEDS`, `MACHINERY`).
4. `V4__reconcile_canonical_domain_model.sql`:
   - Forward-only reconciliation with canonical domain model (`docs/04_domain_model.md`) and API contract (`docs/05_api_contract.md`).
   - Reconciles `refresh_tokens` $\to$ `auth_sessions` (adds `device_fingerprint`, `user_agent`, `ip_address`, `last_active_at`, `is_revoked`).
   - Reconciles `listings` $\to$ `harvest_listings` (adds `crop_type`, `quality_grade`, `allocated_quantity`, `reserved_quantity`, `sold_quantity`, `is_rfq_enabled`, and quantity balance check `allocated_quantity = available + reserved + sold`).
   - Reconciles `harvest_batches` columns to `recorded_quantity`, `unallocated_quantity`, `unit_code`.
   - Reconciles `farm_memberships` role check to include `'MANAGER'` and adds `status` column.
   - Reconciles `user_roles` check constraint to permit all canonical ecosystem roles.

---

## 3. Running & Testing

### Prerequisites
- Java 21 JDK (`C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot` or standard JDK 21).
- Apache Maven 3.9+ or bundled IDE Maven.

### Execute Tests
```bash
# Run unit tests and Spring Modulith architectural verification
mvn test

# Run against a specific profile
mvn test -Dspring.profiles.active=test
```

### Run Locally
```bash
# 1. Start local PostgreSQL 16 (via Docker Compose or native service)
docker compose -f ../infra/docker-compose.yml up -d

# 2. Run Spring Boot application
mvn spring-boot:run
```

- Platform Health Probe: `GET http://localhost:8080/api/v1/health`
- Actuator Metrics: `GET http://localhost:8080/actuator/health`
- Operational Admin Panel: `GET http://localhost:8080/admin`
