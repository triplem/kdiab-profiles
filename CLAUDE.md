# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**kdiab-profiles** is a T1D (Type 1 Diabetes) basal pump profile manager. It allows patients, doctors, and admins to manage insulin pump basal profiles (basal rates, ICR, ISF, and blood glucose targets).

## Repository Structure

```
├── api/              # OpenAPI spec (openapi.yaml) — single source of truth for the API contract
├── backend/          # Ktor (Kotlin) server application
├── frontend/         # React/TypeScript SPA (Vite)
├── docs/             # AsciiDoc architecture docs and ADRs
└── config/           # Detekt config and Keycloak realm JSON
```

## Commands

### Full Stack

```bash
# Start everything (backend + frontend + PostgreSQL + Keycloak)
# Both backend JAR and frontend are built inside Docker — no pre-build step needed.
docker-compose up --build
# Or with Podman:
./manage-podman.sh build && ./manage-podman.sh start

# Include pgadmin (dev tool, not started by default):
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build
```

### Backend

```bash
# Build
./gradlew backend:build

# Unit tests only
./gradlew backend:test

# Integration tests (uses H2 in-memory DB)
./gradlew backend:integrationTest

# E2E tests
./gradlew backend:e2eTest

# Run all checks (unit + integration + e2e + detekt + kover)
./gradlew backend:check

# Run a single test class
./gradlew backend:test --tests "org.javafreedom.kdiab.profiles.domain.model.ProfileTest"

# Static analysis
./gradlew backend:detektMain

# Code coverage (enforces 80% minimum)
./gradlew backend:koverVerify
```

### Frontend

```bash
cd frontend

# Install dependencies
npm install

# Generate API client from OpenAPI spec (do this before building/developing)
npm run api:generate

# Dev server (port 3000, proxies /api to backend on 8080)
npm run dev

# Build
npm run build

# Unit tests (Vitest)
npm test

# Run a single test file
npx vitest run src/__tests__/App.test.tsx

# Lint
npm run lint

# E2E tests (Playwright — requires running app)
npx playwright test
```

## Architecture

### API-First Design

The `api/openapi.yaml` is the contract between frontend and backend. Both sides generate clients/server stubs from it:

- **Backend**: `./gradlew backend:openApiGenerate` generates Kotlin server models into `backend/build/generated/api/`. This runs automatically before `compileKotlin`.
- **Frontend**: `npm run api:generate` generates TypeScript/axios client into `frontend/src/api/generated/`. This runs automatically before `npm run build`.

When changing the API, update `api/openapi.yaml` first, then regenerate on both sides.

### Backend (Hexagonal Architecture)

```
adapters/inbound/web/   # Ktor route handlers (ProfileRoutes, InsulinRoutes) + ProfileMapper
application/service/    # ProfileService — business logic, owns state machine transitions
domain/model/           # Profile, Insulin, segment types, ProfileStatus enum
domain/repository/      # Repository interfaces (ProfileRepository, InsulinRepository)
domain/exception/       # DomainExceptions (BusinessValidationException, ResourceNotFoundException, etc.)
infrastructure/persistence/  # Exposed ORM implementations (ExposedProfileRepository, etc.)
plugins/                # Ktor plugin config (Security, Logging, StatusPages)
```

**ProfileService** implements the core state machine:
- DRAFT → ACTIVE (activate)
- ACTIVE → ARCHIVED + new ACTIVE (copy-on-write on update or activation)
- PROPOSED → ACTIVE (patient accepts doctor proposal) or ARCHIVED (rejects)
- Active profiles are **immutable** — any update creates a new ACTIVE version and archives the old one, preserving history via `previousProfileId`.

**Authentication**: JWT validation against Keycloak. Roles (`PATIENT`, `DOCTOR`, `ADMIN`) come from Keycloak `realm_access.roles`.

**Database**: PostgreSQL via Exposed ORM + HikariCP. Schema migrations via Liquibase. Integration tests use H2 in-memory.
> **PostgreSQL is required for production.** The `IDX_PROFILES_USER_ACTIVE` partial index (`WHERE status = 'ACTIVE'`) enforcing one active profile per user is defined with `dbms: postgresql` in the Liquibase changeset and is not created in H2. Switching to another database engine would lose this constraint.

### Frontend (React + React Query)

- `src/api/generated/` — auto-generated axios client, do not edit manually
- `src/api/client.ts` — axios instance with JWT Bearer token injection (reads from OIDC session storage) and X-Correlation-ID header
- `src/context/TimeFormatContext.tsx` — locale-aware 12/24h time formatting, auto-updates from Keycloak profile locale
- `src/components/` — ProfileList, ProfileEditor, ProfileHistory, AdminInsulinManager
- Authentication via `react-oidc-context` / `oidc-client-ts`; auto-redirects to Keycloak on page load
- Roles are extracted by decoding the JWT access token (Keycloak doesn't always include `realm_access` in the OIDC profile)

### Services (local dev)

| Service | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger |
| Keycloak | http://localhost:8081 |

Test users (password: `password`): `sarah` (PATIENT), `mike` (PATIENT), `dr_house` (DOCTOR), `dr_cameron` (DOCTOR), `admin` (ADMIN).

## Key Design Decisions

All architectural decisions are documented in `docs/adr/`. The most impactful ones:

- **Copy-on-Write profiles** (ADR-015): Active profiles are never mutated in place. Every update archives the current record and creates a new ACTIVE version linked via `previousProfileId`. Clients updating an active profile will receive a **new profile ID** in the response — local state references must be updated.
- **Doctor-Patient collaboration via PROPOSED status** (ADR-016): Doctors can only create profiles in `PROPOSED` state; patients must explicitly accept or reject them. This prevents doctors from directly modifying active treatment profiles without patient consent.
- **No Users table** (ADR-302): `userId` is stored directly as a UUID in the profiles table. There is no separate `Users` entity — identity comes entirely from Keycloak JWT claims.
- **Kotlin-native types in the domain layer** (ADR-105): Use `kotlin.uuid.Uuid`, `kotlinx.datetime.Instant`/`LocalTime` in domain code. Avoid `java.time.*` and `java.util.UUID` in `domain/` and `application/`. Infrastructure layers handle the mapping to Java types required by Exposed/Ktor.
- **Correlation ID tracing** (ADR-110): Every request carries an `X-Correlation-ID` header (generated by the frontend Axios interceptor; extracted/generated by the backend `CallId` plugin). The ID is bound to the SLF4J MDC under `Correlation-ID` so it appears in all log lines without explicit passing.
- **Conventional Commits + Semantic Release** (ADR-111): All commits must follow the Angular Conventional Commits format (`feat:`, `fix:`, `chore:`, `refactor:`, etc.). Semantic Release on `main` automatically determines the version bump and creates GitHub releases.
- **Domain validation in `Profile.validate()`**: Clinical safety checks (max daily basal 150 U/day, ICR/ISF range checks, unit heuristics) live in the domain model, not the service layer.
- **Frontend role resolution from raw JWT**: Roles are parsed from the JWT access token directly because Keycloak's OIDC profile doesn't reliably include `realm_access`.
- **Detekt config**: Rules at `config/detekt/detekt.yml`, baseline at `config/detekt/baseline.xml`. Static analysis runs on `src/main/kotlin` only.
- **Coverage**: Kover enforces 80% minimum; generated API classes (`org.javafreedom.kdiab.profiles.api` package) and `ApplicationKt`/`DatabaseFactory` are excluded.

## Agent Personas

When addressed with `@<persona>`, adopt the corresponding focus:

| Persona | Focus |
|---|---|
| `@Architect` | Hexagonal Architecture, separation of concerns, technology choices |
| `@Developer` | Feature implementation, idiomatic Kotlin/TypeScript, bug fixes |
| `@QA` | Test coverage, edge cases, integration tests |
| `@Reviewer` | Code review, maintainability, subtle edge cases, alternative approaches |
| `@Functional` | Requirements (`docs/requirements.adoc`), business value, API contracts |
| `@Security` | OWASP Top 10, JWT/RBAC (ADR-303), PII/PHI handling |
| `@Docs` | ADRs, `docs/architecture.adoc`, KDoc comments |
| `@DevOps` | `build.gradle.kts`, Dockerfiles, GitHub Actions |
| `@UIUX` | Accessibility, visual consistency, user flows |
