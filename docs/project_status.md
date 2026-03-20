# T1D Basal Profile Service - Project Plan

- [x] **Project Setup & Documentation** <!-- id: 0 -->
    - [x] Initialize Project Structure (Backend & Frontend roots) <!-- id: 1 -->
    - [x] Create Documentation Directory (`docs/`) <!-- id: 2 -->
    - [x] Document Requirements in AsciiDoc (`docs/requirements.adoc`) (Updated with ADRs) <!-- id: 3 -->
    - [x] Document Architecture Decisions (`docs/architecture.adoc`) <!-- id: 4 -->
- [x] **Backend Core & API Design** <!-- id: 5 -->
    - [x] Create OpenAPI Specification (`api/openapi.yaml`) <!-- id: 6 -->
    - [x] Setup Gradle Multi-module project (if needed) or Single module with correct source sets <!-- id: 7 -->
    - [x] Configure Ktor + OpenAPI Generator <!-- id: 8 -->
    - [x] Implement Domain Entities (Hexagonal Core) <!-- id: 9 -->
        - [x] User, Profile, TimeSegments, MedicalData <!-- id: 10 -->
    - [x] Implement Business Logic (Use Cases) <!-- id: 11 -->
        - [x] Create Profile, Activate Profile, Archive Profile <!-- id: 12 -->
        - [x] Implement Profile History & Versioning (Copy-on-Write) <!-- id: 130 -->
- [/] **Persistence Layer** <!-- id: 13 -->
    - [x] Setup PostgreSQL Database (Docker Compose) <!-- id: 14 -->
    - [x] Implement Repository Adapters (Exposed/Ktorm) <!-- id: 15 -->
- [x] **Verification & Deployment** <!-- id: 23 -->
    - [x] Dockerize Application (Dockerfile) <!-- id: 24 -->
    - [/] Comprehensive Testing (Unit/Integration) <!-- id: 25 -->
        - [x] Enforce 80% Test Coverage (ADR-008) (Expanding Coverage) <!-- id: 56 -->
        - [x] Implement BDD E2E Tests using Kotest <!-- id: 131 -->
- [x] **Refactor Profile Model** <!-- id: 26 -->
    - [x] Decouple TimeSegments into independent schedules <!-- id: 27 -->
    - [x] Update API Spec and Domain Models <!-- id: 28 -->
    - [x] Update Persistence (Schema + Repository) <!-- id: 29 -->
- [x] **Security Implementation** <!-- id: 34 -->
    - [x] Add JWT Authentication Dependency <!-- id: 35 -->
    - [x] Configure JWT (Audience, Domain, Realm) <!-- id: 36 -->
    - [x] Implement Role-Based Access Control (RBAC) <!-- id: 37 -->
    - [x] Protect API Routes <!-- id: 38 -->
    - [x] Update ADR <!-- id: 39 -->
- [x] **Refactor Package Structure** <!-- id: 40 -->
    - [x] Move files to `org/javafreedom/kdiab/profiles` <!-- id: 41 -->
    - [x] Update package declarations and imports <!-- id: 42 -->
    - [x] Update build configuration (Gradle, OpenApi) <!-- id: 43 -->
    - [x] Update Application Config <!-- id: 44 -->
    - [x] Add ADR-005 <!-- id: 45 -->
- [x] **Refactor to JSONB Storage** <!-- id: 46 -->
    - [x] Add Exposed JSON dependency <!-- id: 47 -->
    - [x] Update Schema (Profiles table with JSONB) <!-- id: 48 -->
    - [x] Update Repository (Serialize/Deserialize) <!-- id: 49 -->
    - [x] Add ADR-006 (or update existing) <!-- id: 50 -->
- [x] **Implement Kotlinx Serialization** <!-- id: 51 -->
    - [x] Remove Jackson dependencies <!-- id: 52 -->
    - [x] Update OpenAPI Config <!-- id: 53 -->
    - [x] Implement Kotlinx in Codebase (using java.time serializers) <!-- id: 54 -->
    - [x] Add ADR-007 <!-- id: 55 -->
- [x] **Refactor to Kotlin Native Types** <!-- id: 57 -->
    - [x] Add `kotlinx-datetime` and `kotlin.uuid` usage <!-- id: 58 -->
    - [x] Refactor Domain Models (Profile, etc.) <!-- id: 59 -->
    - [x] Refactor Persistence Adapter (Mapping) <!-- id: 60 -->
    - [x] Create ADR-009 (Kotlin Native Types) <!-- id: 61 -->
- [x] **Remove Users Table** <!-- id: 30 -->
    - [x] Remove Users object from Tables.kt <!-- id: 31 -->
    - [x] Update Profiles table definition <!-- id: 32 -->
    - [x] Add ADR-003 <!-- id: 33 -->
- [x] **Frontend Application (React/TS)** <!-- id: 19 -->
    - [x] Setup Vite + React + TypeScript <!-- id: 20 -->
    - [x] Implement Profile Management UI (Viewing, Expanding, and Editing) <!-- id: 21 -->
- [x] **Verification & Deployment** <!-- id: 23 -->
    - [x] Dockerize Application (Dockerfile) <!-- id: 24 -->
    - [x] Comprehensive Testing (Unit/Integration) <!-- id: 25 -->
- [x] **Adopt Sonar for Static Analysis** <!-- id: 62 -->
    - [x] Update ADR-008 to include Sonar <!-- id: 63 -->
    - [x] Add Sonar Gradle Plugin <!-- id: 64 -->
    - [x] Configure Sonar Properties <!-- id: 65 -->
    - [x] Verify Sonar Integration <!-- id: 66 -->
- [x] **Implement Detekt for Static Analysis** <!-- id: 106 -->
    - [x] Add Detekt Gradle Plugin <!-- id: 107 -->
    - [x] Configure Detekt Rules (detekt.yml) <!-- id: 108 -->
    - [x] Integrate with Gradle Check <!-- id: 109 -->
- [x] **Implement Backend CI Workflow** <!-- id: 67 -->
    - [x] Create GitHub Action Workflow <!-- id: 68 -->
    - [x] Document CI Process in Architecture Doc <!-- id: 69 -->
- [x] **Replace Jacoco with Kover** <!-- id: 117 -->
    - [x] Remove Jacoco Plugin & Config <!-- id: 118 -->
    - [x] Add Kover Plugin <!-- id: 119 -->
    - [x] Configure Kover Rules & Exclusions <!-- id: 120 -->
    - [x] Verify Coverage Report Generation <!-- id: 121 -->

- [x] **Detailed Documentation & Best Practices** <!-- id: 70 -->
    - [x] Configure Asciidoctor Plugin <!-- id: 71 -->
    - [x] Optimize CI with Gradle Build Action <!-- id: 72 -->
    - [x] Create GitHub Pages Workflow <!-- id: 73 -->

- [x] **Refactor Architecture Documentation** <!-- id: 74 -->
    - [x] Restructure for logical flow (Common -> Specific) <!-- id: 75 -->
    - [x] Group ADRs by Topic <!-- id: 76 -->
- [x] Group ADRs by Topic <!-- id: 76 -->

- [x] **Refactor API Documentation & ADRs** <!-- id: 77 -->
    - [x] Create Separate API Section <!-- id: 78 -->
    - [x] Renumber ADRs logically <!-- id: 79 -->

- [x] **Document Development Tooling** <!-- id: 80 -->
    - [x] Create ADR-010 (Gradle, GitHub, AsciiDoc) <!-- id: 81 -->
    - [x] Update Architecture Doc <!-- id: 82 -->

- [x] **Restructure ADRs by Category** <!-- id: 83 -->
    - [x] Renumber to 1xx, 2xx, 3xx <!-- id: 84 -->
    - [x] Rename files and update headers <!-- id: 85 -->
    - [x] Update references in docs <!-- id: 86 -->

- [x] **Define AI Agent Personas** <!-- id: 87 -->
    - [x] Create docs/ai-agents.adoc <!-- id: 88 -->
    - [x] Link in Architecture Doc <!-- id: 89 -->

- [x] **Configure Dependabot** <!-- id: 90 -->
    - [x] Create .github/dependabot.yml <!-- id: 91 -->

- [x] **Configure AI Agents** <!-- id: 92 -->
    - [x] Create .cursorrules <!-- id: 93 -->

- [x] **Implement Role-Based Access (Doctor/Admin)** <!-- id: 94 -->
    - [x] Update Requirements <!-- id: 95 -->
    - [x] Update ADR-303 <!-- id: 96 -->
    - [x] Implement UserPrincipal & Rules <!-- id: 97 -->

- [x] **Implement Delete Operations** <!-- id: 98 -->
    - [x] Update Repository Interface & Impl <!-- id: 99 -->
    - [x] Implement Service Logic (Segment Deletion) <!-- id: 100 -->
    - [x] Add Delete Routes <!-- id: 101 -->

- [x] **Implement Global Exception Handling (ADR-106)** <!-- id: 102 -->
    - [x] Create Custom Exception Classes <!-- id: 103 -->
    - [x] Configure Ktor StatusPages Plugin <!-- id: 104 -->
    - [x] Refactor Routes to throw exceptions <!-- id: 105 -->

- [x] **Refactor to Idiomatic Kotlin** <!-- id: 140 -->
    - [x] Use Expression Bodies in Service/Repo <!-- id: 141 -->
    - [x] Use StatusPages for Route Error Handling <!-- id: 142 -->
    - [x] Restore Principal interface in Security <!-- id: 143 -->
    - [x] Clean up imports and lint errors <!-- id: 144 -->

- [x] **Maintenance & Optimization** <!-- id: 150 -->
    - [x] Refactor Gradle Build & Dependencies <!-- id: 151 -->
    - [x] Standardize Experimental Opt-ins (Global) <!-- id: 152 -->

- [/] **Insulin Management Feature** <!-- id: 160 -->
    - [x] Backend Domain and Persistence (ExposedInsulinRepository) <!-- id: 161 -->
    - [x] Backend API Routes (InsulinRoutes) and E2E Tests <!-- id: 162 -->
    - [/] Frontend Integration (TimeInput, ProfileEditor integration) <!-- id: 163 -->
