# Switchly API

Switchly manages typed feature flags for projects and evaluates them independently in each environment. The backend uses Java 21, Spring Boot, PostgreSQL 17 and Flyway.

## Run locally

From the workspace directory containing `api/` and `infra/`, start the database and API with Docker Compose.

```sh
export SWITCHLY_JWT_SECRET="$(openssl rand -hex 32)"
docker compose -f infra/docker-compose.yml up --build -d
curl --fail http://localhost:8080/healthz
```

Keep the JWT signing secret stable across restarts that need to preserve sessions. Compose stores PostgreSQL data in a named volume. The API listens on port 8080 and the database is mapped to port 15432.

## Use the API

Register at `POST /api/v1/auth/register` or sign in at `POST /api/v1/auth/login`. Use the returned access token to manage organizations, projects, environments and flags. Project administrators can issue environment-scoped SDK keys for backend applications.

The API guide includes a first evaluation, the management endpoints, targeting operators, segment definitions, rollout bucketing and batch requests. [Switchly API guide](switchly-api-docs.md)

## Test

Run the wrapper from this API directory with a JDK supporting Java 21 and Docker available to Testcontainers.

```sh
./mvnw test
```

The suite exercises the pure evaluator, management and SDK authentication, PostgreSQL migrations, repositories and end-to-end HTTP behavior.
