# Startup Guide

## Prerequisites

-   Docker and Docker Compose
-   OR Podman and podman-compose

## Quick Start

To start the full application (Backend + Frontend + Database + Keycloak), run:

```bash
docker-compose up --build
# Or with podman:
# podman-compose up --build
```

Access the services at:

-   **Frontend**: [http://localhost:3000](http://localhost:3000)
-   **Backend API**: [http://localhost:8080/api/v1](http://localhost:8080/api/v1) (proxied via frontend at [http://localhost:3000/api](http://localhost:3000/api))
-   **Swagger UI**: [http://localhost:8080/swagger](http://localhost:8080/swagger)
-   **Keycloak**: [http://localhost:8081](http://localhost:8081)

### Test Users
The application is pre-configured with several test accounts in Keycloak. The password for all accounts is `password`.

| Username | Role | Description |
| ---- | ---- | ---- |
| `sarah` | `PATIENT` | T1D Patient |
| `mike` | `PATIENT` | T1D Patient |
| `dr_house` | `DOCTOR` | Doctor (Allowed patients: sarah) |
| `dr_cameron` | `DOCTOR` | Doctor (Allowed patients: mike) |
| `admin` | `ADMIN` | Super Administrator |

## Development Setup

### Backend
The backend is a Ktor application running on port 8080. It connects to Postgres and Keycloak.

### Frontend
The frontend is a React application running on port 3000 (mapped to Nginx port 80).
It serves the UI and proxies `/api` requests to the backend to avoid CORS issues.

## Data Persistence
Postgres data is persisted in a named volume `postgres_data`. To reset the database, run:

```bash
docker-compose down -v
# Or with podman:
# podman-compose down -v
```

Additional information can be found in the ADRs.