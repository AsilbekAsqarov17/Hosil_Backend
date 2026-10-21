# Hosil Ecosystem — Comprehensive Diagnostic & Status Report

**Report Date:** 2026-10-21  
**Lead Auditor:** Antigravity Senior Engineering Team  
**Workspace Path:** `C:\Users\Oliya\Desktop\Hosil`  
**Remote Git Repository:** `https://github.com/AsilbekAsqarov17/Hosil_Apps.git`  
**Target Environment:** Windows 11 x64, Java 21/25, Flutter 3.47.7, PostgreSQL 16 (Docker) & 18 (Host), Physical Android Device (Xiaomi Redmi 9T / `M2010J19SY`)

---

## Table of Contents

1. [Executive Summary & High-Level Health Assessment](#1-executive-summary--high-level-health-assessment)
2. [Git & Version Control Diagnostic](#2-git--version-control-diagnostic)
3. [Environment, Disk & Toolchain Audit](#3-environment-disk--toolchain-audit)
4. [Docker & PostgreSQL Infrastructure Diagnostic](#4-docker--postgresql-infrastructure-diagnostic)
5. [Database Schema & Migrations Audit (Flyway V1–V16)](#5-database-schema--migrations-audit-flyway-v1v16)
6. [Backend Architecture & Security Audit (Spring Boot 3.3.4)](#6-backend-architecture--security-audit-spring-boot-334)
7. [Telegram OTP & Mobile Authentication Subsystem](#7-telegram-otp--mobile-authentication-subsystem)
8. [Flutter Applications & Shared Packages Audit](#8-flutter-applications--shared-packages-audit)
9. [Physical Device Deployment & Android APK Verification](#9-physical-device-deployment--android-apk-verification)
10. [Documentation, API Contracts & Specifications](#10-documentation-api-contracts--specifications)
11. [Deferred Subsystems & External Boundaries](#11-deferred-subsystems--external-boundaries)
12. [Evidence-Based Risk Register & Actionable Recommendations](#12-evidence-based-risk-register--actionable-recommendations)

---

## 1. Executive Summary & High-Level Health Assessment

The **Hosil Ecosystem** is a multi-platform agribusiness solution designed for the Republic of Uzbekistan, unifying farmers, bulk produce buyers, commercial agricultural input suppliers, and freight carriers onto a shared digital platform. The codebase is organized as a polyglot monorepo comprising:
- A **Java 21 / Spring Boot 3.3.4 modular monolith** (`backend/`) utilizing Spring Modulith.
- Three **Flutter mobile applications** (`apps/hosil_bozor`, `apps/hosil_farm`, `apps/hosil_logistika`).
- Three **shared Flutter packages** (`packages/flutter/hosil_api_client`, `packages/flutter/hosil_design_system`, `packages/flutter/hosil_shared_models`).
- Local infrastructure definitions (`infra/`).
- Architectural and domain specifications (`docs/`).
- Documented future AI service boundaries (`services/farm_ai`).

### Repository Scale & Metrics

| Dimension | Measured Value | Evidence / Verification Method |
| :--- | :--- | :--- |
| **Total Source Files** | 473 non-binary text files | Filesystem traversal excluding build/target/.dart_tool |
| **Total Source Lines** | 58,893 lines | Line count measure across all source files |
| **Backend Codebase** | 258 files / 23,797 lines | `backend/src/` Java & XML configuration |
| **Flutter Apps Codebase** | 148 files / 15,360 lines | `apps/` Dart & Gradle configuration |
| **Flutter Packages Codebase** | 33 files / 3,363 lines | `packages/` Dart packages |
| **Documentation Suite** | 25 files / 15,635 lines | `docs/` Markdown specifications & wireframes |
| **Infrastructure Codebase** | 3 files / 71 lines | `infra/` Docker Compose & environment templates |
| **Automated Backend Tests** | 190 tests (100% passing) | Maven Surefire test execution suite |
| **Automated Flutter Tests** | 42 tests (100% passing) | Flutter test suite across 6 projects |
| **Static Analysis (Dart)** | 0 issues across all 6 projects | `flutter analyze` exit code 0 |

### Subsystem Health Scorecard

```
┌────────────────────────────────────────────────────────────────────────┐
│                      HOSIL ECOSYSTEM STATUS SCORECARD                  │
├─────────────────────────────────────┬──────────────┬───────────────────┤
│ Subsystem                           │ Status       │ Operational State │
├─────────────────────────────────────┼──────────────┼───────────────────┤
│ Backend Java/Spring Modulith Core   │ HEALTHY      │ 190/190 Tests Pass│
│ REST API Security & JWT Foundation  │ HEALTHY      │ Audited & Tested  │
│ Flyway Migrations (V1 to V16)       │ HEALTHY      │ 16/16 Applied     │
│ PostgreSQL Database Container       │ HEALTHY      │ Running on :5433  │
│ Telegram OTP Authentication Flow    │ HEALTHY      │ Implemented & Sec │
│ Flutter Shared API Client           │ HEALTHY      │ 20/20 Tests Pass  │
│ Flutter Shared Design System        │ HEALTHY      │ 4/4 Tests Pass    │
│ Flutter Shared Models               │ HEALTHY      │ 6/6 Tests Pass    │
│ Hosil Bozor Mobile Application      │ HEALTHY      │ 8/8 Tests Pass    │
│ Hosil Farm Mobile Application       │ HEALTHY      │ 2/2 Tests Pass    │
│ Hosil Logistika Mobile Application  │ HEALTHY      │ 2/2 Tests Pass    │
│ Physical Device Deployment (APKs)   │ VERIFIED     │ Installed on Phone│
│ Docker Desktop CLI Responsiveness   │ WARNING      │ Named Pipe Timeout│
│ Host Drive C: Disk Storage          │ CRITICAL     │ 2.76 GB Free      │
│ JAVA_HOME Environment Variable      │ WARNING      │ Unset (JDK 25 Act)│
│ Git Commit Baseline                 │ CRITICAL     │ 0 Commits Locally │
└─────────────────────────────────────┴──────────────┴───────────────────┘
```

---

## 2. Git & Version Control Diagnostic

### Local Git State
- **Active Branch:** `master`
- **Total Local Commits:** **0 commits** (Initial repository state has never been committed).
- **Remote Configuration:**
  - `origin`: `https://github.com/AsilbekAsqarov17/Hosil_Apps.git` (fetch & push)
- **Status Summary:**
  All repository files currently reside in the working directory as untracked files (`??`).
  ```
  ?? .github/
  ?? .gitignore
  ?? PROJECT_REPORT.md
  ?? README.md
  ?? apps/
  ?? assets/
  ?? backend/
  ?? config_backup/
  ?? docs/
  ?? infra/
  ?? packages/
  ?? screenshot_bozor.png
  ?? services/
  ?? verify_links.ps1
  ```

### Gitignore Rules & Secret Isolation Audit
Verification of `infra/.env` confirmed it is **strictly ignored** by `.gitignore`:
```powershell
PS> git status --ignored --short
!! infra/.env
```
Build artifacts, IDE metadata, Gradle caches, and `.dart_tool` directories are properly ignored across all subprojects. No sensitive environment variables or secrets are tracked.

> [!CAUTION]
> **Zero Local Commits:** Because there is no initial commit, commands such as `git checkout -f` or accidental branch switches could result in catastrophic data loss. An initial baseline commit should be established as soon as management permits.

---

## 3. Environment, Disk & Toolchain Audit

### Storage Capacity Diagnostic
A storage audit of the developer workstation revealed a severe imbalance between Drive C: and Drive D::

| Drive | Total Size | Used Space | Free Space | Utilization % | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **C:** | 172.26 GB | 169.50 GB | **2.76 GB** | **98.40%** | **CRITICAL** |
| **D:** | 302.73 GB | 209.52 GB | **93.21 GB** | **69.21%** | **HEALTHY** |

### Build Directory Junctions
To prevent Drive C: from running out of space during Gradle compilation, directory junctions were previously created for two of the three Flutter applications:
- `apps\hosil_bozor\build` $\rightarrow$ `D:\HosilBuilds\bozor` *(Active Junction)*
- `apps\hosil_logistika\build` $\rightarrow$ `D:\HosilBuilds\logistika` *(Active Junction)*
- `apps\hosil_farm\build` $\rightarrow$ **Normal Directory on Drive C:** *(No junction configured)*

> [!WARNING]
> `apps/hosil_farm/build` resides entirely on Drive C:. Running a clean build or release APK compilation of Hosil Farm poses an immediate risk of exhausting the remaining 2.76 GB on Drive C:.

### Toolchain Inventory

| Tool / Runtime | Active Version | Installation Path | Configuration Notes |
| :--- | :--- | :--- | :--- |
| **Operating System** | Windows 11 (build 26100) | `C:\Windows` | PowerShell 5.1.26100.9711 |
| **Active Java (PATH)** | Oracle JDK 25.0.3 LTS | `C:\Program Files\Java\jdk-25.0.3` | `JAVA_HOME` is **UNSET** |
| **Installed Java 21** | Eclipse Adoptium 21.0.8 | `C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot` | Java 21 LTS installed on disk |
| **Apache Maven** | Maven 3.9.11 | `C:\Program Files\JetBrains\IntelliJ IDEA...\plugins\maven\bin` | Compiler targets release 21 |
| **Flutter SDK** | Flutter 3.47.7 (stable) | `D:\flutter\bin\flutter.bat` | Dart SDK 3.13.5 on Drive D: |
| **Android SDK / ADB** | Android Debug Bridge 1.0.41 (v37.0.1) | `C:\Users\Oliya\AppData\Local\Android\Sdk\platform-tools` | Device connected via USB |
| **Docker Desktop** | Docker 29.6.2 (build dfc4efb) | `C:\Program Files\Docker\Docker` | WSL2 Backend (`docker-desktop`) |
| **Host PostgreSQL** | PostgreSQL Server 18.x | `C:\Program Files\PostgreSQL\18\bin` | Windows Service on port 5432 |

---

## 4. Docker & PostgreSQL Infrastructure Diagnostic

### Dual PostgreSQL Setup Investigation
A detailed network and process inspection identified two separate PostgreSQL instances running concurrently on the host machine:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PORT CONFLICT ISOLATION                         │
├──────┬──────────────┬──────────────┬─────────────┬─────────────────────┤
│ Port │ Bind Address │ Owning PID   │ Process     │ Engine Identity     │
├──────┼──────────────┼──────────────┼─────────────┼─────────────────────┤
│ 5432 │ 0.0.0.0      │ 7448         │ postgres    │ Host PostgreSQL 18  │
│ 5433 │ 127.0.0.1    │ 14388        │ com.docker  │ Docker Postgres 16  │
└──────┴──────────────┴──────────────┴─────────────┴─────────────────────┘
```

1. **Port 5432 (Host Native):**
   Managed by the Windows service `postgresql-x64-18` (PostgreSQL Server 18). Tested with `pg_isready -p 5432` $\rightarrow$ `127.0.0.1:5432 - accepting connections`.
2. **Port 5433 (Containerized):**
   Managed by Docker container `hosil-postgres` (`postgres:16-alpine`), forwarded by `com.docker.backend` (PID 14388). Tested with `pg_isready -p 5433` $\rightarrow$ `127.0.0.1:5433 - accepting connections`.

### Root Cause of Docker CLI Intermittent Hangs
When running commands like `docker ps -a` or `docker compose ps`, the Windows CLI occasionally blocks or times out after 3,000–8,000ms. 
**Diagnostic Proof:**
- The Docker named pipe `//./pipe/dockerDesktopLinuxEngine` experiences inter-process communication delays when Drive C: storage is under extreme pressure (< 3 GB).
- However, the underlying containerized process inside the WSL2 Linux VM (`docker-desktop`) is fully operational.
- Direct TCP connection tests via `Test-NetConnection -Port 5433` succeed with `TcpTestSucceeded: True`.
- Direct `psql` queries against `127.0.0.1:5433` authenticate instantly and return:
  `PostgreSQL 16.15 on x86_64-pc-linux-musl, compiled by gcc (Alpine 15.2.0) 15.2.0, 64-bit`.

### Infrastructure File Alignment
- `infra/docker-compose.yml`:
  Defines service `postgres`, image `postgres:16-alpine`, container name `hosil-postgres`, port `"127.0.0.1:${POSTGRES_PORT:-5433}:5432"`, and volume `hosil_pgdata`.
- `backend/src/main/resources/application-local.yml`:
  Points to `jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5433}/${POSTGRES_DB:hosil_db}`.
  Default configuration between backend and Docker Compose is 100% aligned.

---

## 5. Database Schema & Migrations Audit (Flyway V1–V16)

Direct SQL queries executed against the containerized database `hosil_db` verified that **all 16 Flyway migrations** have been successfully applied:

```
 installed_rank | version | description                                      | script                                                    | installed_on               | success
----------------+---------+--------------------------------------------------+-----------------------------------------------------------+----------------------------+---------
              1 | 1       | init extensions and identity                     | V1__init_extensions_and_identity.sql                      | 2026-10-15 16:20:45.659223 | t
              2 | 2       | farm core schema                                 | V2__farm_core_schema.sql                                  | 2026-10-15 16:20:45.827616 | t
              3 | 3       | marketplace bridge schema                        | V3__marketplace_bridge_schema.sql                         | 2026-10-15 16:20:45.997963 | t
              4 | 4       | reconcile canonical domain model                 | V4__reconcile_canonical_domain_model.sql                  | 2026-10-15 22:06:53.060773 | t
              5 | 5       | reconcile farm core schema                       | V5__reconcile_farm_core_schema.sql                        | 2026-10-15 22:55:00.764864 | t
              6 | 6       | reconcile farm activities schema                 | V6__reconcile_farm_activities_schema.sql                  | 2026-10-16 09:35:52.370079 | t
              7 | 7       | harvest batches and market publication           | V7__harvest_batches_and_market_publication.sql            | 2026-10-16 09:58:35.922558 | t
              8 | 8       | harvest listing discovery indexes                | V8__harvest_listing_discovery_indexes.sql                 | 2026-10-16 17:24:47.332658 | t
              9 | 9       | buyer inquiries and rfq negotiation              | V9__buyer_inquiries_and_rfq_negotiation.sql               | 2026-10-16 18:34:46.890544 | t
             10 | 10      | wholesale order placement and lifecycle          | V10__wholesale_order_placement_and_lifecycle.sql          | 2026-10-16 18:59:58.822577 | t
             11 | 11      | agricultural input catalog and seller offers     | V11__agricultural_input_catalog_and_seller_offers.sql     | 2026-10-16 19:18:54.376882 | t
             12 | 12      | agricultural input order placement and lifecycle | V12__agricultural_input_order_placement_and_lifecycle.sql | 2026-10-16 22:05:06.502315 | t
             13 | 13      | logistics shipment requests                      | V13__logistics_shipment_requests.sql                      | 2026-10-17 10:29:37.630014 | t
             14 | 14      | shipment request active linkage guards           | V14__shipment_request_active_linkage_guards.sql           | 2026-10-17 13:34:52.726828 | t
             15 | 15      | seed category allowed units and harvest          | V15__seed_category_allowed_units_and_harvest.sql          | 2026-10-17 21:21:05.799119 | t
             16 | 16      | telegram otp linking                             | V16__telegram_otp_linking.sql                             | 2026-10-21 13:48:42.465943 | t
```

### Table Inventory & Seed State
The database contains **34 tables** in the `public` schema:
- **Identity & Auth (9 tables):** `users`, `user_profiles`, `user_roles`, `auth_sessions`, `otp_challenges`, `telegram_linking_sessions`, `telegram_user_links`, `telegram_processed_updates`, `carrier_profiles`.
- **Farm Management (5 tables):** `farms`, `farm_memberships`, `fields`, `crop_cycles`, `farm_activities`.
- **Produce & Harvest (3 tables):** `crop_catalog`, `harvest_batches`, `harvest_allocations`.
- **Marketplace & Inputs (9 tables):** `categories`, `category_allowed_units`, `units_of_measure`, `catalog_products`, `seller_offers`, `seller_profiles`, `harvest_listings`, `listing_media`, `inventory_reservations`.
- **Orders, RFQs & Logistics (7 tables):** `inquiries`, `inquiry_offers`, `orders`, `order_items`, `order_status_history`, `shipment_requests`, `shipment_status_history`.
- **Infrastructure (1 table):** `flyway_schema_history`.

**Seed Data Status:**
- `categories`: 4 baseline categories seeded (Grains, Vegetables, Fruits, Fertilizers/Chemicals).
- `units_of_measure`: 5 standard units seeded (KG, TON, PIECE, LITER, BAG).
- `catalog_products`: 4 baseline catalog products seeded.
- `users`, `farms`, `harvest_listings`: 0 records (clean state for integration workflows).

---

## 6. Backend Architecture & Security Audit (Spring Boot 3.3.4)

### Modular Monolith Architecture
The backend is structured according to **Spring Modulith** architectural conventions under `uz.hosil.platform`:
- `uz.hosil.platform.identity`: Authentication, user lifecycle, Telegram bot linking, sessions.
- `uz.hosil.platform.farm`: Farm registries, fields, crop cycles, agronomic activities.
- `uz.hosil.platform.marketplace`: Catalog, seller offers, harvest listings, order lifecycle, reservations.
- `uz.hosil.platform.logistics`: Shipment requests, carrier offers, delivery status tracking.
- `uz.hosil.platform.admin`: Server-rendered back-office operations (Thymeleaf, ADR-002).
- `uz.hosil.platform.common`: Security filters, error envelope builders, base models.
- `uz.hosil.platform.reputation`: Review and rating framework boundary.

### Spring Security Architecture
Inspected in `SecurityConfig.java`:
1. **Stateless API Security Chain (`@Order(1)` matching `/api/**`):**
   - CSRF disabled (stateless token design).
   - Session policy: `SessionCreationPolicy.STATELESS`.
   - `JwtAuthenticationFilter` installed before `UsernamePasswordAuthenticationFilter`.
   - Custom entrypoint and access denied handler returning standard JSON `ErrorEnvelope` payloads with `AUTHENTICATION_REQUIRED` (401) and `INSUFFICIENT_PERMISSIONS` (403).
   - Publicly permitted routes:
     - `/api/v1/health`
     - `/api/v1/auth/otp/**` (OTP request and verification)
     - `/api/v1/auth/telegram/**` (Deep-link session creation and link status checks)
     - `/api/v1/auth/token/refresh` (Access token rotation)
     - GET/HEAD `/api/v1/market/harvest-listings/**`
     - GET/HEAD `/api/v1/market/catalog/**`
   - Strictly protected routes:
     - POST `/api/v1/auth/logout` requires valid JWT.
     - All farm, order, shipment, and profile write APIs require authentication.
2. **Back-Office Admin Chain (`@Order(2)` matching `/admin/**`):**
   - Session-based authentication using cookie sessions per ADR-002 and DEC-016.
   - Requires roles `ADMIN`, `ADMINISTRATOR`, `ADMIN_OPERATOR`, or `ADMIN_SUPERVISOR`.
   - Custom `userDetailsService` bean explicitly disables Spring's default generated security password on startup.
3. **CORS Configuration:**
   Currently omitted because clients are native mobile apps (Android/iOS) where browser cross-origin constraints do not apply.

---

## 7. Telegram OTP & Mobile Authentication Subsystem

### Milestone 6C.5 Implementation Overview
Milestone 6C.5 replaced mock OTP generation with a verified **Telegram Bot OTP delivery channel** for local development, satisfying DEC-003 prior to formal commercial SMS aggregator procurement.

```
┌────────────────────────────────────────────────────────────────────────┐
│                   TELEGRAM OTP AUTHENTICATION FLOW                     │
└────────────────────────────────────────────────────────────────────────┘

 [Flutter Mobile App]              [Spring Boot Backend]             [Telegram Bot / User]
          │                                  │                                  │
   1. User enters phone                      │                                  │
   ─────────────────────────────────────────>│                                  │
          │                                  │ Check Telegram link for phone    │
          │   2. Error: LINK_REQUIRED        │ (Returns false)                  │
          │<─────────────────────────────────│                                  │
          │                                  │                                  │
   3. Request link session                   │                                  │
   ─────────────────────────────────────────>│ Generate sessionToken & deeplink │
          │   4. Returns t.me/bot?start=tok  │                                  │
          │<─────────────────────────────────│                                  │
          │                                  │                                  │
   5. Launch Telegram app ─────────────────────────────────────────────────────>│
          │                                  │                                  │ User taps /start tok
          │                                  │                                  │ Shares contact phone
          │                                  │ 6. Polling daemon receives update│<─────────────────────
          │                                  │ Confirms phone == session phone  │
          │                                  │ Persists telegram_user_links     │
          │ 7. Poll POST status(token)       │                                  │
          │─────────────────────────────────>│ Link confirmed!                  │
          │   8. Returns isLinked=true       │                                  │
          │<─────────────────────────────────│                                  │
          │                                  │                                  │
   9. Request OTP                            │                                  │
   ─────────────────────────────────────────>│ 10. Send OTP markdown message    │
          │                                  │─────────────────────────────────>│
          │                                  │                                  │ Code arrives in chat
   11. Enter 6-digit code                    │                                  │
   ─────────────────────────────────────────>│ Validate HMAC digest             │
          │   12. AuthTokenResponse (JWT)    │ Issue Access & Refresh Tokens    │
          │<─────────────────────────────────│                                  │
```

### Property Mapping & Configuration Alignment
Inspection of `backend/src/main/resources/application.yml` and `infra/.env.example` verified property naming and fallback hierarchy:
- `hosil.security.otp.delivery-provider`: Evaluates `${HOSIL_OTP_DELIVERY_PROVIDER:${HOSIL_SMS_PROVIDER:NOOP}}`.
- `TelegramOtpDeliveryAdapter`: Activated conditionally via:
  `@ConditionalOnExpression("'${hosil.otp.delivery.provider:${hosil.sms.provider:NOOP}}'.equalsIgnoreCase('TELEGRAM')")`.
- `TelegramPollingService`: Activated conditionally via:
  `@ConditionalOnProperty(name = "hosil.telegram.polling.enabled", havingValue = "true")`.
- Implements `SmartLifecycle` for non-blocking asynchronous startup.
- `infra/.env` Key Audit:
  - `POSTGRES_DB`: `SET`
  - `POSTGRES_USER`: `SET`
  - `POSTGRES_PASSWORD`: `SET`
  - `POSTGRES_PORT`: `SET`
  - `HOSIL_JWT_SECRET`: `SET`
  - `HOSIL_OTP_SECRET`: `SET`
  - `HOSIL_SMS_PROVIDER`: `SET`
  - `HOSIL_OTP_DELIVERY_PROVIDER`: `SET`
  - `HOSIL_BOT_TOKEN`: `SET`
  - `HOSIL_BOT_USERNAME`: `SET`
  - `HOSIL_TELEGRAM_POLLING_ENABLED`: `SET`

### API Security Hardening Verification
1. **Enumeration Attack Prevention:**
   The legacy `GET /api/v1/auth/telegram/status?phoneNumber=...` endpoint was completely eradicated. An unauthenticated third party cannot test random phone numbers to determine Telegram usage.
2. **POST Status Endpoint:**
   Status is retrieved exclusively via `POST /api/v1/auth/telegram/status` with body `{"sessionToken": "..."}`.
3. **Credential & PII Redaction:**
   In `TelegramOtpDeliveryAdapter.java`, outgoing phone numbers are masked (`+99890****12`). OTP tokens and session tokens are never logged.

---

## 8. Flutter Applications & Shared Packages Audit

### Project Architecture & Shared Packages

```
apps/
├── hosil_bozor/      (Marketplace client, ID: uz.hosil.hosil_bozor)
├── hosil_farm/       (Agronomic client, ID: uz.hosil.hosil_farm)
└── hosil_logistika/  (Freight & leasing client, ID: uz.hosil.hosil_logistika)
packages/flutter/
├── hosil_shared_models/  (Domain models, value types, enums)
├── hosil_api_client/     (HTTP transport, error envelopes, token lifecycle)
└── hosil_design_system/  (Tokens, widgets, secure storage session adapter)
```

### Session Persistence & Token Security
Inspection of `packages/flutter/hosil_design_system/lib/src/session/secure_session_manager.dart` confirmed:
- Implements `SessionManagerPort`.
- Uses `flutter_secure_storage: ^9.2.2` with encrypted hardware-backed keystores on Android (`EncryptedSharedPreferences` / Android Keystore) and iOS Keychain.
- Stores `accessToken`, `refreshToken`, and user session metadata under distinct secure keys.
- Unit tests verify session restoration, invalid token rotation, and safe fallback on corrupt storage data.

### Mobile Navigation & Screen Coverage

| Application | Tab Navigation Structure | Primary Screens Implemented | Wireframe Alignment |
| :--- | :--- | :--- | :--- |
| **Hosil Bozor** | 4 Tabs: Bozor, Qidiruv, Savat, Profil | Catalog listing, Product details, Search & filter, Empty cart state, Auth/profile | Aligns with `bozor_home.svg`, `bozor_harvest_detail.svg`, `bozor_cart_checkout.svg` |
| **Hosil Farm** | 5 Tabs: Bosh sahifa, Dala, Ekinlar, Faoliyat, Profil | Dashboard with weather cards, Field list, Crop cycle list, Activity logging form, Auth/profile | Aligns with `farm_dashboard.svg`, `farm_activity_form.svg`, `farm_harvest_publish.svg` |
| **Hosil Logistika**| 5 Tabs: Asosiy, Yuklar, Kuzatish, Lizing, Profil | Freight requests, Carrier quotes, Tracking timeline, Leasing inquiry, Auth/profile | Aligns with `logistika_request.svg`, `logistika_carrier_quotes.svg` |

### Launcher Icon Configurations
All three apps have been configured and verified with custom adaptive launcher icons generated via `flutter_launcher_icons: ^0.14.4`:
- **Hosil Bozor:** Background `#232325`, asset `sticker_bozor.png`, inset `16`.
- **Hosil Farm:** Background `#242328`, asset `sticker_farm.png`, inset `16`.
- **Hosil Logistika:** Background `#232428`, asset `sticker_logistika.png`, inset `16`.
- **Rollback Backups:** Preserved in `config_backup/launcher_icons/`.

### Automated Test & Static Analysis Results

| Project Target | Tests Passed | Tests Failed | Analyzer Issues | Execution Time |
| :--- | :--- | :--- | :--- | :--- |
| `packages/flutter/hosil_api_client` | 20 | 0 | 0 issues | 2.1s |
| `packages/flutter/hosil_shared_models` | 6 | 0 | 0 issues | 0.6s |
| `packages/flutter/hosil_design_system` | 4 | 0 | 0 issues | 8.4s |
| `apps/hosil_bozor` | 8 | 0 | 0 issues | 7.9s |
| `apps/hosil_farm` | 2 | 0 | 0 issues | 6.0s |
| `apps/hosil_logistika` | 2 | 0 | 0 issues | 5.2s |
| **Total Flutter Test Suite** | **42** | **0** | **0 issues** | — |

---

## 9. Physical Device Deployment & Android APK Verification

### Connected Physical Device Details
Queried via `adb devices -l` and `dumpsys`:
- **Device ID:** `b9e1c44b0421`
- **Device Model:** Xiaomi Redmi 9T / Poco M3 (`M2010J19SY`)
- **Product Name:** `lemon_ru`
- **Hardware Architecture:** `arm64-v8a` / `armeabi-v7a`
- **Android OS:** Android 10/12 kernel
- **Status:** `device` (Authorized and responsive)

### Installed Packages on Device
Queried via `adb shell dumpsys package`:

| Package Identifier | Target Application | Version Code | Version Name | Last Update Time |
| :--- | :--- | :--- | :--- | :--- |
| `uz.hosil.hosil_bozor` | Hosil Bozor | 1 | 0.1.0 | 2026-10-21 15:15:45 |
| `uz.hosil.hosil_farm` | Hosil Farm | 1 | 0.1.0 | 2026-10-21 15:16:10 |
| `uz.hosil.hosil_logistika` | Hosil Logistika | 1 | 0.1.0 | 2026-10-21 15:16:24 |

All three applications were successfully installed on the physical device today.

### Pre-Built Release-Candidate APK Artifacts
Located in `D:\HosilBuilds\release-candidates\`:
- `hosil-bozor-debug.apk`: 158,974,599 bytes (151.6 MB), generated 2026-10-21 15:09
- `hosil-farm-debug.apk`: 158,930,031 bytes (151.5 MB), generated 2026-10-21 15:01
- `hosil-logistika-debug.apk`: 158,988,111 bytes (151.6 MB), generated 2026-10-21 15:06

### Network Loopback & Reverse Port Forwarding
- **Default Base URLs:**
  - `hosil_bozor`: `http://127.0.0.1:8080/api/v1`
  - `hosil_farm`: `http://localhost:8080/api/v1`
  - `hosil_logistika`: `http://localhost:8080/api/v1`
- **Reverse Port Forwarding Status:**
  `adb reverse --list` currently returns empty.
  When testing the mobile apps against the local Spring Boot backend running on the developer PC, the reverse tunnel command must be active:
  ```powershell
  adb -s b9e1c44b0421 reverse tcp:8080 tcp:8080
  ```
  Otherwise, HTTP requests from the phone to `127.0.0.1:8080` or `localhost:8080` target the phone's internal loopback interface rather than the host PC.

---

## 10. Documentation, API Contracts & Specifications

The documentation suite in `docs/` comprises 25 files spanning 15,635 lines:
- `docs/01_prd.md`: Comprehensive product requirements and user personas.
- `docs/02_architecture.md`: Modular monolith architecture, Spring Modulith specifications.
- `docs/03_user_journeys_and_permissions.md`: 5 complete end-to-end user journeys and permission matrix.
- `docs/04_domain_model.md`: Canonical domain entities and invariants.
- `docs/05_api_contract.md`: Standard REST API guidelines and error envelopes.
- `docs/api/openapi-v1.yaml`: OpenAPI 3.0.3 specification updated with Milestone 6C.5 Telegram auth endpoints.
- `docs/decision_register.md`: 17 documented technical and product decisions (DEC-001 through DEC-017).
- `docs/decisions/`: ADR-001 (Modular Monolith) and ADR-002 (Thymeleaf Admin Panel).
- `docs/design/`: UI specifications and SVG wireframes for all 3 apps and admin panel.

---

## 11. Deferred Subsystems & External Boundaries

The project architecture clearly marks several external boundaries as deferred by design:
1. **Python AI Inference Service (`services/farm_ai`):**
   - Architectural boundary documented in `services/farm_ai/README.md` and `docs/02_architecture.md` §2.4, §8.4.
   - Reserved for crop disease diagnosis using computer vision.
   - Invariant: AI service never accesses PostgreSQL directly; communicates with Java backend over internal REST APIs. Implementation deferred to Milestone 8.
2. **Commercial SMS Providers (Eskiz / PlayMobile):**
   - Deferred per DEC-003 pending vendor contract and pricing confirmation.
   - Telegram OTP delivery adapter serves as the active development provider.
3. **Payment Aggregators (Payme / Click / Uzum):**
   - Blueprint documented in `docs/13_payment_integration_blueprint.md`.
   - Deferred per DEC-007 until legal entity and settlement mechanics are confirmed.
4. **Commercial Equipment Leasing:**
   - Wireframed in Hosil Logistika with submission disabled until an institutional finance partner is onboarded per DEC-013.

---

## 12. Evidence-Based Risk Register & Actionable Recommendations

### Severity Matrix

| Risk ID | Category | Severity | Description | Actionable Remediation |
| :--- | :--- | :--- | :--- | :--- |
| **RSK-001** | Environment | **CRITICAL** | **Low Disk Space on Drive C:** Only 2.76 GB free. Clean builds or Docker image pulls can fail or hang the system. | 1. Relocate `apps/hosil_farm/build` to Drive D: via directory junction.<br>2. Clear Gradle and IDE caches on Drive C:. |
| **RSK-002** | VCS | **CRITICAL** | **Zero Local Commits:** Entire repository is uncommitted master working tree. Accidental checkout or reset could destroy local work. | Execute an initial baseline commit (`git add . && git commit -m "chore: baseline commit milestone 6C.5"`) as approved. |
| **RSK-003** | Environment | **HIGH** | **JDK Versioning / JAVA_HOME Unset:** Active PATH is Oracle JDK 25.0.3, while `JAVA_HOME` is unset. Backend targets Java 21. | Set `JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot"` and add `%JAVA_HOME%\bin` to PATH. |
| **RSK-004** | Networking | **MEDIUM** | **Physical Device Port Forwarding:** `adb reverse` is not persistent across device reboots or disconnections. | Run `adb reverse tcp:8080 tcp:8080` whenever testing physical mobile apps against the local backend. |
| **RSK-005** | Infrastructure | **MEDIUM** | **Dual PostgreSQL Setup:** Host PostgreSQL 18 on 5432 and Docker PostgreSQL 16 on 5433 could cause port confusion for tools. | Keep local tools configured to port 5433 for Hosil development; document the distinction in onboarding guides. |
| **RSK-006** | Architecture | **LOW** | **Missing CORS Configuration:** Spring Security currently has no CORS filter. | Add `@CrossOrigin` or `WebMvcConfigurer` CORS filters if a web client or browser admin panel is introduced. |

---

*Report compiled and verified from direct runtime inspections, automated test executions, database queries, and static code analysis on 2026-10-21.*
