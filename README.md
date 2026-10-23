# Hosil Backend — Modular Monolith Platform

Server-side core for the Hosil agricultural ecosystem in Uzbekistan, built with Java 21, Spring Boot 3.3.4, Spring Modulith, PostgreSQL 16, and Flyway.

---

## Architecture & Layout

```
Hosil_Backend/
├── backend/
│   ├── pom.xml                     # Maven project definition (Java 21, Spring Boot 3.3.4)
│   ├── Dockerfile                  # Multi-stage production container image
│   └── src/
│       ├── main/
│       │   ├── java/uz/hosil/platform/
│       │   │   ├── admin/          # Server-rendered admin console (Thymeleaf)
│       │   │   ├── common/         # Security, JWT, error envelopes, rate limiting
│       │   │   ├── farm/           # Field management & crop health diagnostics
│       │   │   ├── identity/       # Phone OTP, Telegram linking & auth sessions
│       │   │   ├── logistics/      # Equipment leasing & freight management
│       │   │   ├── marketplace/    # Catalog, orders, escrow & payment lifecycles
│       │   │   └── reputation/     # Two-sided reviews & blind moderation
│       │   └── resources/
│       │       ├── application.yml         # Development defaults (in-memory H2)
│       │       ├── application-staging.yml # Containerized staging profile
│       │       ├── application-prod.yml    # Production profile with strict security gates
│       │       └── db/migration/           # Flyway migrations V1 through V20
│       └── test/                   # Unit, Modulith architecture, and integration tests
├── infra/
│   ├── docker-compose.yml          # Local development PostgreSQL 16 container (port 5433)
│   ├── docker-compose.staging.yml  # Isolated staging topology (PostgreSQL on 5434, Backend on 8081)
│   ├── .env.example                # Template for environment variables
│   └── scripts/                    # Durable backup, restore verification, and cleanup scripts
├── docs/
│   ├── api/
│   │   └── openapi-v1.yaml         # Authoritative canonical OpenAPI 3.0 specification
│   ├── 00_documentation_index.md  # System documentation map
│   ├── 01_prd.md                   # Product requirements & user journeys
│   ├── 02_architecture.md          # Architectural invariants & module boundaries
│   ├── 04_domain_model.md          # Relational entities, enums, and tables
│   ├── 05_api_contract.md          # HTTP conventions & error envelopes
│   ├── decision_register.md        # Architecture decision records (DEC-001..DEC-017)
│   └── reports/                    # Milestone verification and pilot readiness reports
├── services/
│   └── farm_ai/                    # Boundary specification for future Python inference service
└── .github/
    └── workflows/
        └── backend-ci.yml          # Independent Backend Continuous Integration
```

---

## Authoritative API Specification

This repository is the **single source of truth** for the Hosil API contract:
- Canonical OpenAPI spec: [`docs/api/openapi-v1.yaml`](docs/api/openapi-v1.yaml)
- Contract version: `1.0.0-rc2`

Any API changes must be made here first, validated via contract tests, and synchronized as a versioned snapshot to `Hosil_Frontend`.

---

## Local Development & Testing

### Prerequisites
- JDK 21 (Eclipse Temurin recommended)
- Maven 3.9+
- Docker & Docker Compose

### 1. In-Memory Build & Fast Tests (H2)
```bash
cd backend
mvn clean test
```

### 2. Start Local PostgreSQL 16 (Port 5433)
```bash
docker compose -f infra/docker-compose.yml up -d
```

### 3. Run Full PostgreSQL & Flyway Integration Suite (V1–V20)
```bash
cd backend
mvn test -Ppostgres-it
```

---

## Staging & Production Operations

### Start Staging Environment (Isolated Ports 5434 & 8081)
```bash
docker compose -f infra/docker-compose.staging.yml up -d --build
```

### Database Backup & Disaster Recovery
```powershell
# Create external durable backup on host
powershell -ExecutionPolicy Bypass -File infra/scripts/backup_staging.ps1

# Test restore into isolated disposable database
powershell -ExecutionPolicy Bypass -File infra/scripts/restore_staging.ps1 -DumpPath infra/backups/<backup_file>.dump

# Purge synthetic staging data between test cohorts
powershell -ExecutionPolicy Bypass -File infra/scripts/cleanup_synthetic_staging_data.ps1 -ConfirmExecution
```
