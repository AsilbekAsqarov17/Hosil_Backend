# Hosil Ecosystem — Monorepo

**Repository:** https://github.com/AsilbekAsqarov17/Hosil_Apps.git  
**Version:** 0.1.0-SNAPSHOT  
**Architecture:** Polyglot Agricultural Platform (Flutter 3.x + Spring Boot 3.3.4 Modular Monolith + PostgreSQL 16 + Isolated Python AI Boundary)

---

## 1. Monorepo Structure

```
.
├── backend/                              # Java 21 + Spring Boot 3.3.4 Modular Monolith & Admin Panel
│   ├── pom.xml                           # Pinned dependencies & Spring Modulith starter
│   ├── src/main/java/uz/hosil/platform/  # Domain modules: identity, farm, marketplace, logistics, reputation, admin
│   ├── src/main/resources/               # application.yml, application-local.yml, Thymeleaf templates
│   └── src/main/resources/db/migration/  # Flyway versioned SQL migrations (V1, V2, V3)
├── apps/
│   ├── hosil_bozor/                      # Marketplace Flutter Mobile Application (B2B/B2C)
│   ├── hosil_farm/                       # Farm Operations & Offline Daily Helper Mobile Application
│   └── hosil_logistika/                  # Freight Logistics & Machinery Leasing Mobile Application
├── packages/flutter/
│   ├── hosil_design_system/              # Reusable Flutter UI tokens, theme, outdoor contrast, widgets
│   ├── hosil_api_client/                 # Shared HTTP REST client foundation & ErrorEnvelope mapping
│   └── hosil_shared_models/              # Shared client domain enums (UserRole, SyncStatus, MeasurementUnit)
├── infra/
│   ├── docker-compose.yml                # Local PostgreSQL 16 infrastructure
│   └── .env.example                      # Environment variables template
├── services/
│   └── farm_ai/                          # Documented Python/FastAPI AI boundary (Milestone 8)
└── docs/                                 # Authoritative documentation tree (PRD, Architecture, ERD, API)
```

---

## 2. Technology Stack & Architectural Decisions

| Layer / Subsystem | Technology | Canonical Spec & Decision Record |
| :--- | :--- | :--- |
| **Main Backend** | Java 21, Spring Boot 3.3.4 | [`docs/02_architecture.md`](docs/02_architecture.md), [`ADR-001`](docs/decisions/ADR-001_modular_monolith.md) |
| **Persistence** | PostgreSQL 16, Flyway, Spring Data JPA | [`docs/04_domain_model.md`](docs/04_domain_model.md) |
| **Admin Back-Office**| Spring Boot + Thymeleaf + HTMX | [`ADR-002`](docs/decisions/ADR-002_admin_panel_thymeleaf.md), [`05_admin_panel_ui.md`](docs/design/05_admin_panel_ui.md) |
| **Mobile Clients** | Flutter 3.x (Dart 3.x) | [`docs/design/01_shared_design_system.md`](docs/design/01_shared_design_system.md) |
| **Offline Persistence**| Drift SQLite (Hosil Farm) | [`docs/07_offline_strategy.md`](docs/07_offline_strategy.md) |
| **AI Crop Scanner** | Python 3.11+, FastAPI (Stateless Inference)| `services/farm_ai/README.md` (Milestone 8) |

---

## 3. Local Development Quickstart

### Step 1: Start PostgreSQL Infrastructure
```bash
# 1. Create .env from template
cp infra/.env.example infra/.env

# 2. Start PostgreSQL 16 container
docker compose -f infra/docker-compose.yml up -d

# Verify container health
docker compose -f infra/docker-compose.yml ps
```

### Step 2: Build & Run Main Backend
```bash
cd backend

# Run automated tests and modular boundary verification
mvn test

# Start the Spring Boot development server
mvn spring-boot:run
```

- Platform Health Probe: `http://localhost:8080/api/v1/health`
- Spring Actuator Metrics: `http://localhost:8080/actuator/health`
- Back-Office Admin Operations: `http://localhost:8080/admin`

### Step 3: Run Mobile Applications (Flutter)
Ensure the Flutter 3.16+ SDK is installed and added to PATH.

```bash
# 1. Run Hosil Bozor (Marketplace)
cd apps/hosil_bozor
flutter pub get
flutter run

# 2. Run Hosil Farm (Farm Assistant)
cd apps/hosil_farm
flutter pub get
flutter run

# 3. Run Hosil Logistika (Freight)
cd apps/hosil_logistika
flutter pub get
flutter run
```

---

## 4. Security & Configuration Boundaries

- **Zero Secrets Committed:** Production credentials, private keys (`*.pem`, `*.key`), and live `.env` files are excluded by `.gitignore`.
- **OTP Delivery Architecture (`DEC-003` / Milestone 6C.5):**
  - Uses `OtpDeliveryPort` allowing interchangeable dispatch adapters without altering core identity logic or mobile apps.
  - **Local Development / Physical Phone Testing:** Uses `TelegramOtpDeliveryAdapter` with dedicated bot (`@hosil_authentication_bot`). Users link their phone number via a single-use deep link `https://t.me/<bot_username>?start=<token>`. Once linked, real 6-digit OTP codes are delivered securely directly to their Telegram app.
  - **Testing Configuration in `infra/.env`:**
    ```bash
    HOSIL_OTP_DELIVERY_PROVIDER=TELEGRAM
    HOSIL_BOT_TOKEN=8787469508:AAHIxDiZrtcK46ZWGNv12D3g3kdTZmzIqo8
    HOSIL_BOT_USERNAME=hosil_authentication_bot
    ```
  - **Production:** Replaced with commercial telecom adapter (`Eskiz` or `PlayMobile`) with zero mobile app changes.
- **Provider-Neutral Payment (`DEC-007`):** Payment settlement logic is deferred pending legal/tax counsel.
