# Switchly

Feature flags API. Orgs own projects, projects own flags. You create a flag, flip it on or off, that's most of it.

This is the backend only. Spring Boot 4, Java 21, Maven. Nothing is persisted — everything lives in memory, so a restart wipes the board.

## Run it

Need JDK 21+ and that's about it. Wrapper is already in the repo.

```
./mvnw spring-boot:run
```

Listens on `8080`. Tests:

```
./mvnw test
```

## Shape of the data

1. Create an organization
2. Create a project under that org
3. Create flags under the project

Flag keys have to be lowercase letters, numbers, and hyphens (`dark-mode` is fine, `Dark Mode` is not). New flags start disabled. Description is optional.

Duplicate keys in the same project return 409.

## HTTP

Base path is `/api/v1`.

- `POST /orgs` — `{ "name": "Acme" }`
- `GET /orgs` / `GET /orgs/{orgId}`
- `POST /orgs/{orgId}/projects` — `{ "name": "Web App" }`
- `GET /orgs/{orgId}/projects` / `GET /projects/{projectId}`
- `POST /projects/{projectId}/flags` — `{ "key", "name", "description?" }`
- `GET /projects/{projectId}/flags` / `GET /flags/{flagId}`
- `PUT /flags/{flagId}/state` — `{ "enabled": true }`
- `DELETE /flags/{flagId}` — 204, empty body

Quick walkthrough:

```
curl -s -X POST localhost:8080/api/v1/orgs \
  -H 'Content-Type: application/json' \
  -d '{"name":"Acme"}'

curl -s -X POST localhost:8080/api/v1/orgs/<orgId>/projects \
  -H 'Content-Type: application/json' \
  -d '{"name":"Web App"}'

curl -s -X POST localhost:8080/api/v1/projects/<projectId>/flags \
  -H 'Content-Type: application/json' \
  -d '{"key":"new-checkout","name":"New Checkout","description":"3-step flow"}'

curl -s -X PUT localhost:8080/api/v1/flags/<flagId>/state \
  -H 'Content-Type: application/json' \
  -d '{"enabled":true}'
```

Errors look like this:

```json
{"error":{"code":"NOT_FOUND","message":"Flag … not found"}}
```

`NOT_FOUND` / `VALIDATION_FAILED` / `CONFLICT`. Validation failures are 400.

## Layout

Controllers → services → in-memory repos. Packages under `live.switchly.api`. Tests sit next to the same structure in `src/test/java`.

No auth, no database, no UI. If you're looking for those, they aren't here yet.
