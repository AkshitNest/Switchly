# RC-1 verification record

Local release rehearsal on 22 November 2026 using Docker Engine 29.6.1, Compose 5.2.0, Java 21, PostgreSQL 17, installed Chrome and a locally hosted Ollama model. Requests traversed Caddy over HTTPS with a local CA. The dedicated test overlay published a loopback API fixture port, raised auth quotas and shortened access tokens to one minute so the browser exercised real expiry.

| Check | Observed result |
| --- | --- |
| Spring, Flyway and PostgreSQL | 255 tests passed, zero failures, errors or skips. Included a populated V9 → V10 upgrade, composite project/organization constraints, concurrent admin changes and refresh rotation. |
| Frontend unit suite | 10 tests passed, zero failures or skips. |
| Playwright | All 10 browser scenarios passed, including the real model, invitation acceptance/cancellation, role reassignment/removal, SDK evaluation and revocation. Ran through the HTTPS deployment configuration. |
| Build | Both production Dockerfiles built successfully. The web build type-checked TypeScript and emitted a separately loaded Three.js bundle. |
| TLS and credentials | Local certificate chain verified, HTTP redirected, HSTS present, and cookie had Secure, HttpOnly and SameSite=Strict. Direct Spring HTTP ignored an untrusted forwarded HTTPS header. The release configuration publishes only TCP 80 and 443. |
| Database privileges | The app login had superuser, database creation and role creation all disabled. Bootstrapping a fresh cluster with separate file-mounted secrets reached Flyway V10. |
| Restore and shutdown | See the machine-readable recovery record for the archive size, table fingerprint, health transitions, signal exit codes and canary count. A restored-only write verified the cutover target. |

[Named browser cases and backend totals](acceptance.txt) · [Recovery record](recovery.json) · [Desktop screenshot](dashboard-desktop.png) · [Mobile screenshot](dashboard-mobile.png)

The tested local API image ID was `sha256:0227598f7493de3ce2401d80346afaaa3694261c97b418b50e9d7ca691fa3225`. The console image ID was `sha256:8bcceb0e1b48e50dd68f3497b7dadfcec467f7a22aa2a3eef13eb879bfd8fca3`. These identify Linux ARM64 artifacts. The [local promotion tags](rc1-images.env) point to those immutable IDs. The packaged archive loaded successfully with the same two image IDs. Its SHA-256 is `933a03e87e5143c23589668df51eddfa2b59bb36781a9fa17213bdbeedcbdfb0`. Record the registry digest if promoting the release through a registry.

## Authorization and invitation audit

- Spring resolves the signed membership on incoming bearer requests and rechecks current organization, role and project assignment in the service layer. Employees see only their project in listings and only their own membership in the roster. Direct flag, environment, rollout, targeting, preview, SDK and AI draft routes were included in boundary tests.
- Invites use 32 random bytes. The database stores the digest, intended email, organization, role, project and expiry. Creation, reissue, cancellation and redemption serialize on the organization. Invitation mismatches, wrong existing-account passwords, replay and expiration were rejected. Failed password verification preserved the invitation for its intended recipient.
- Role changes take effect on already-issued sessions. Removing membership invalidates its access and refresh path. Accounts with several memberships choose an organization after proving their password. An active session cannot pivot to another organization by changing identifiers.
- The V10 upgrade deliberately gives legacy employees zero assigned projects. The composite foreign key rejects a sibling organization's project, including through a direct database write. The database allows a null employee assignment for migration and the application denies project access in that state.
- SDK authentication remains distinct from console authentication. Grants bind to project and environment IDs. An admin manages the lifetime of issued SDK keys separately from the user's membership. Invitations are organization-owned until cancelled, consumed or expired.

## Operational findings addressed

The development Compose file published raw API/database ports, used development database credentials and lacked app service probes. The production overlay isolates those listeners, mounts secrets per service, limits the application database account and rotates service logs. The console now responds to SIGTERM by draining requests. Screenshot review found the organization icon covering its name in the sidebar. The spacing fix has a browser regression check. The catch-all Java error handler records the exception type without printing arbitrary exception messages, and production SQL error logging is restricted. Synthetic canaries checked passwords, signing material, session and invitation tokens, SDK credentials and error bodies. Raw logs, database dumps and private keys are excluded from this evidence bundle.

## Repeat the rehearsal

Run from the repository root on a test host with free loopback ports 18087, 18088 and 18443. Use a Docker-accessible Ollama server with `qwen:latest` installed. The browser suite creates disposable users and projects. The recovery drill deliberately stops its isolated stack and tests an outage.

```bash
set -euo pipefail
work=$(mktemp -d)
infra/ops/init-secrets.sh "$work/secrets"
export COMPOSE_PROJECT_NAME=switchly-rc1-drill
export COMPOSE_FILE=infra/docker-compose.yml:infra/production.compose.yml:infra/ops/rc1-drill.compose.yml
export SWITCHLY_SECRETS_DIR="$work/secrets" SWITCHLY_DOMAIN=localhost
export SWITCHLY_API_IMAGE=switchly-api:rc1-drill SWITCHLY_WEB_IMAGE=switchly-web:rc1-drill
docker build -t "$SWITCHLY_API_IMAGE" api
docker build -t "$SWITCHLY_WEB_IMAGE" web
docker compose up -d --wait --wait-timeout 150
docker compose exec -T edge cat /data/caddy/pki/authorities/local/root.crt > "$work/localhost-ca.crt"
curl --cacert "$work/localhost-ca.crt" --fail https://localhost:18443/healthz
export TEST_CERT_SPKI="$(openssl s_client -connect localhost:18443 -servername localhost </dev/null 2>/dev/null |
  openssl x509 -pubkey -noout | openssl pkey -pubin -outform DER |
  openssl dgst -sha256 -binary | openssl base64 -A)"
export CONSOLE_URL=https://localhost:18443 TEST_API_ORIGIN=http://127.0.0.1:18088
(cd api && ./mvnw test)
(cd web && npm ci && npm test && npm run build && npm run test:e2e)
python3 infra/ops/verify-recovery.py --ca "$work/localhost-ca.crt" --backup-dir "$work/backups"
docker compose down
```

For a repeated run with the same Compose project and volumes, reuse its existing secret directory. The test imports the **public** local CA certificate and limits Chrome's exception to the specific local leaf public key. The production runbook uses normal public certificate verification.

A separate GnuPG encryption/decryption drill recovered a 72,308-byte archive into a second local PostgreSQL cluster with independent database passwords. The checksum passed, Flyway reported V10, the restored database contained 22 flags and the API returned healthy. Off-host transfer and key retrieval still need to be exercised at the deployment site.

The local drill covers logical restore, container recreation and a database switchback with RC-1. Production DNS, public ACME renewal, host loss, off-host encrypted backup retrieval, workload capacity, monitoring delivery and a cross-version rollback with a real previous release remain deployment gates. Off-host RPO/RTO have not been measured. This release is suitable for staging and a controlled pilot after those gates are signed off. [Deployment checklist and rollback procedure](../DEPLOYMENT.md)
