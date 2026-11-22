# Switchly release console

The React and TypeScript console manages feature releases through the Spring API. The [release runbook](../infra/DEPLOYMENT.md) covers deployment, Ollama configuration and recovery. A same-origin Node service serves the Vite build, keeps user tokens on the server, and forwards management requests. PostgreSQL remains the source of truth.

## Run the complete application

For a fresh installation, run these commands at the workspace root with Docker Compose available. The first command creates a local signing secret only if the file is absent. For an existing installation, use its current signing secret.

```sh
test -f infra/.env || (umask 077 && openssl rand -hex 32 | sed 's/^/SWITCHLY_JWT_SECRET=/' > infra/.env)
docker compose --env-file infra/.env -f infra/docker-compose.yml up --build -d
curl --fail http://127.0.0.1:8080/healthz
```

Open the [local console](http://127.0.0.1:3000). Register an account, choose **New project**, and create the first flag. Registration provisions an organization with ADMIN membership. Project creation provisions development, staging, and production. Compose keeps application data in a PostgreSQL volume. Keep the signing secret stable between starts.

To stop the stack and retain its database:

```sh
docker compose --env-file infra/.env -f infra/docker-compose.yml down
```

## Develop the frontend

Use Node 24 and npm. Start the API and database, then run the console with Vite hot reload. If the full stack is already running, use port 3001 for development.

```sh
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d api
cd web
npm ci
cp -n .env.example .env
PORT=3001 npm run dev
```

Open the [development console](http://127.0.0.1:3001). For a standalone production build, use `npm run build` followed by `npm start`. Serve the app over HTTP so that its Node session service and API routes are available.

| Setting | Purpose |
| --- | --- |
| `API_ORIGIN` | Server-side Spring origin. Defaults to `http://127.0.0.1:8080`. Compose uses `http://api:8080`. |
| `PORT`, `HOST` | Console listener. Local defaults are port 3000 on loopback. |
| `COOKIE_SECURE` | Set to `true` behind HTTPS. The local HTTP Compose setup uses `false`. Compose accepts `SWITCHLY_COOKIE_SECURE` as the override. |
| `SWITCHLY_JWT_SECRET` | Backend signing secret. Supply through the backend environment. Browser builds do not need it. |

## Pages and access

| Page | Operations |
| --- | --- |
| Sign in and registration | Create an account, sign in, restore a session on reload, sign out |
| Workspace and project overview | Sign in to an organization, select an authorized project, inspect per-environment flag counts, open recently updated logical flags |
| Feature flags | Search and filter, create BOOLEAN, STRING, NUMBER and JSON flags, edit names/descriptions/defaults, confirm deletion |
| Environment configuration | Select an environment, toggle its state, edit its typed value, order targeting rules, select segments, set or clear an integer rollout |
| Environments | List, create, rename, describe, and delete custom environments |
| Segments | Create/edit AND or OR condition groups, preview membership using a supplied context, remove unused segments |
| SDK keys | Issue keys for selected environments, copy the one-time secret, list metadata and revoke keys |

Sessions bind to one membership. Employees manage flags, targeting, rollouts and segments in their assigned project. Admins manage all projects, environments, memberships and SDK keys in their organization. Every management request is authorized again by Spring.

The rule editor stages edits, moves and removals until **Save rules**. Clearing the rollout sends a JSON null. Setting 0% or 100% sends an integer and retains the backend's stable-user-ID requirement. Numeric and nested JSON values use lossless parsing and serialization. The console explains decision order and uses the backend for segment membership.

## Integrated backend endpoints

All paths are relative to `/api/v1`.

| Use | Methods and paths |
| --- | --- |
| Authentication | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`. `GET /auth/me` |
| Organization and role | `GET /orgs`. `GET /orgs/{orgId}/members` |
| Project selection | `GET, POST /orgs/{orgId}/projects` |
| Environments | `GET, POST /projects/{projectId}/environments`. `PUT, DELETE /environments/{environmentId}` |
| Logical flags | `GET, POST /projects/{projectId}/flags`. `PUT, DELETE /flags/{flagId}` |
| Environment state and value | `GET /environments/{environmentId}/flags` and `/flags/{flagId}` beneath that environment. `PUT /environments/{environmentId}/flags/{flagId}/state` and `/value` |
| Targeting and rollout | `GET, PUT /environments/{environmentId}/flags/{flagId}/targeting` and `/rollout` |
| Segments | `GET, POST /projects/{projectId}/segments`. `PUT, DELETE /projects/{projectId}/segments/{key}`. `POST /projects/{projectId}/segments/{key}/evaluate` |
| SDK keys | `GET, POST /projects/{projectId}/sdk-keys`. `DELETE /projects/{projectId}/sdk-keys/{keyId}` |

The Node service returns the user profile to the browser on sign-in and registration, and sets an opaque HttpOnly, SameSite=Strict session cookie. It rotates expired access tokens with a single shared refresh operation per session, including across browser tabs. A rejected refresh ends the session. Mutations require a same-origin console header. The gateway accepts a fixed list of management routes.

An SDK secret appears only in the issuing administrator's creation dialog. Closing the dialog discards the value. Copying is an explicit administrator action. The console does not persist credentials in web storage, and its gateway excludes the server-side SDK evaluation endpoints. The end-to-end test uses a separate server-side test client to verify an issued key and its revocation.

The MVP session store lives in one Node process. Restarting the console requires signing in again. Multiple frontend replicas would need shared session storage and coordinated token rotation. The backend sees the gateway's address for rate limiting in the default configuration.

## Verification

Run the unit suite and compile the production assets from `web/`:

```sh
npm ci
npm test
npm run build
```

The browser suite uses real PostgreSQL, Spring, the containerized console and installed Google Chrome. Use the dedicated Compose project from the workspace root. Its higher test quotas and one-minute JWT lifetime apply to that isolated stack. The tests create their own users and resources.

```sh
export SWITCHLY_JWT_SECRET="$(openssl rand -hex 32)"
docker compose -p switchly-ui-test -f infra/docker-compose.yml -f web/test/stack.compose.yml up --build -d
curl --retry 30 --retry-delay 1 --retry-all-errors --fail http://127.0.0.1:18086/healthz
(cd web && npm ci && npm run test:e2e)
docker compose -p switchly-ui-test -f infra/docker-compose.yml -f web/test/stack.compose.yml down
unset SWITCHLY_JWT_SECRET
```

The test frontend listens on port 13086. `CONSOLE_URL`, `TEST_API_ORIGIN` and `PLAYWRIGHT_CHANNEL` override the test defaults. Use the one-minute token lifetime when running the real-expiry case. The responsive test saves desktop and mobile dashboard screenshots in `web/test-results/`. The integration suite checks both roles, enforced backend denials, precise typed values, scoped credentials, ordered targeting, rollout clearing, destructive confirmations, mobile navigation and session renewal.

Run the Spring regression suite separately with Docker available:

```sh
(cd api && ./mvnw test)
```

The backend guide defines accepted types, permissions, targeting precedence and evaluation reasons in detail. [Switchly API guide](../api/switchly-api-docs.md)
