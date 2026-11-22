# Switchly RC-1 deployment

Use the production overlay for a single Docker host running PostgreSQL 17, the Spring API, the Node console and Caddy. Only Caddy publishes ports. The release was exercised on Docker Desktop with a local certificate authority. The target host still needs the checklist below. [RC-1 verification evidence](evidence/RC-1.md)

## Prepare the release

Run commands from the repository root. Use Docker Compose with `!reset` and `!override` support (2.24.4 or later), Bash, OpenSSL and `shasum`. The rehearsal also needs Python 3, Chrome, Node 24 and the configured Ollama model. Build once on the target CPU architecture or ship the same tested multi-platform image digests through your registry. Give the deployment operator access to Docker and protect that account as a host administrator.

The local release bundle contains the two tested Linux ARM64 application images. On an ARM64 deployment host, copy the release files, verify their manifest against the checksum received through the release channel, and load them before the first start. Caddy and PostgreSQL are pulled by digest. The bundle excludes database contents and deployment secrets.

```bash
(cd infra/release-artifacts && shasum -a 256 -c SHA256SUMS && docker load -i switchly-rc1-linux-arm64.tar.gz)
```

For the first installation, set the domain and create a **new** private directory. Supply the real hostname in place of the example. Retain the same deployment environment and secrets across subsequent runs.

```bash
set -euo pipefail
export SWITCHLY_DOMAIN=flags.example.com
export RELEASE_DIR="$HOME/switchly-rc1"
umask 077
mkdir -p "$RELEASE_DIR"
infra/ops/init-secrets.sh "$RELEASE_DIR/secrets"
# On the audited build host these release tags point to the tested local images.
. infra/evidence/rc1-images.env
docker image inspect "$SWITCHLY_API_IMAGE" "$SWITCHLY_WEB_IMAGE" --format '{{.Id}} {{.Os}}/{{.Architecture}}'
docker image save "$SWITCHLY_API_IMAGE" "$SWITCHLY_WEB_IMAGE" | gzip > "$RELEASE_DIR/rc1-images.tar.gz"
(cd "$RELEASE_DIR" && shasum -a 256 rc1-images.tar.gz > rc1-images.sha256)
cat > "$RELEASE_DIR/deployment.env" <<SETTINGS
COMPOSE_PROJECT_NAME=switchly-live
COMPOSE_FILE=infra/docker-compose.yml:infra/production.compose.yml
SWITCHLY_DOMAIN=$SWITCHLY_DOMAIN
SWITCHLY_SECRETS_DIR=$RELEASE_DIR/secrets
SWITCHLY_API_IMAGE=$SWITCHLY_API_IMAGE
SWITCHLY_WEB_IMAGE=$SWITCHLY_WEB_IMAGE
SWITCHLY_DATABASE=switchly
OLLAMA_ORIGIN=http://host.docker.internal:11434
OLLAMA_MODEL=qwen:latest
SETTINGS
set -a
. "$RELEASE_DIR/deployment.env"
set +a
docker compose config --quiet
docker image inspect --format '{{.Id}}' "$SWITCHLY_API_IMAGE" "$SWITCHLY_WEB_IMAGE" > "$RELEASE_DIR/rc1-image-ids"
```

The tested images target Linux ARM64. For a different target, run the builds and acceptance rehearsal on that architecture, then use those verified image references in the deployment environment.

Use shell-safe, space-free paths for the sourced environment file. Preserve the environment, release image archive or registry digests, image IDs, Compose files, Caddyfile, encrypted copies of all three secret files and access to the Caddy data volume. Use a unique immutable tag for each subsequent release. An archive transferred to another host can be checked and loaded with `shasum -a 256 -c rc1-images.sha256 && docker load -i rc1-images.tar.gz` from its directory. The overlay pins the tested Caddy and PostgreSQL digests. Review and rehearse security updates before changing them.

### Secrets and database privileges

The PostgreSQL bootstrap password, application database password and JWT key are independent. Compose grants individual file mounts to the services that use them. PostgreSQL reads its bootstrap password from a file. Spring imports its two secrets as a config tree. The application role owns its tables and can run Flyway migrations in its database. The initialization script gives it login and schema privileges with superuser, role creation, database creation and replication disabled. The separate bootstrap account is for maintenance. The initialization script runs only on an empty database volume. Migrate a pre-existing development cluster by logical backup and fresh restore into the production cluster. [Compose secrets](https://docs.docker.com/compose/how-tos/use-secrets/)

Protect the host disk and backup destination with encryption. Keep the secret directory mode 0700. Its individual mounts must be readable by the non-root application users. Changing a password file on an initialized volume requires a corresponding database password change and API recreation. Coordinate JWT rotation with session invalidation, including stored refresh tokens. Treat Docker access, certificate keys and logical dumps as privileged.

### TLS, network and model

Point the domain's A/AAAA records at the host and permit inbound TCP 80 and 443. Verify IPv6 routing if an AAAA record exists. Caddy handles HTTP redirects, automatic certificate issuance/renewal and HSTS. It routes the two SDK evaluation paths and the minimal database readiness endpoint to Spring. Browser management traffic goes through the same-origin Node session gateway. Preserve the edge-data volume. The production cookie has Secure, HttpOnly and SameSite=Strict. [Caddy reverse proxy](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy)

The production configuration leaves Spring's trusted proxy list empty. The gateway constructs upstream headers, including its own bearer token. It discards browser authorization and forwarding headers. Rate limits at Spring aggregate by adjacent service address, so login quotas can be shared by many users. Measure expected concurrency and enforce per-client quotas at a trusted ingress before opening public registration. An external load balancer or CDN requires a verified source-IP chain, direct-origin access controls and another spoofed-header test. Keep PostgreSQL, the raw management API, the Caddy admin API and Ollama off the public network.

Set `OLLAMA_ORIGIN` to the private endpoint reachable **from the web container**. On Linux, a daemon bound only to host loopback may be unreachable through the Docker gateway. Restrict any additional listener to the trusted network. Provision the selected model (for the tested configuration, `ollama pull qwen:latest`), then verify a draft through the UI. The operator's release brief reaches that model. Decide who may submit briefs before enabling public access.

## First start and release checklist

```bash
docker compose up -d --wait --wait-timeout 150 postgres
docker compose up -d --wait --wait-timeout 150 api web
docker compose exec -T postgres psql -XAt -U postgres -d "$SWITCHLY_DATABASE" \
  -c "SELECT version,success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"
docker compose up -d --wait --wait-timeout 150 edge
docker compose ps
curl -I "http://$SWITCHLY_DOMAIN/"
curl --fail --show-error --silent "https://$SWITCHLY_DOMAIN/healthz"
openssl s_client -connect "$SWITCHLY_DOMAIN:443" -servername "$SWITCHLY_DOMAIN" \
  -verify_hostname "$SWITCHLY_DOMAIN" -verify_return_error </dev/null
```

- [ ] Record the release image IDs/digests, migration version (`10|t`), architecture, configuration revision, release owner and rollback owner.
- [ ] Verify HTTPS and the certificate chain from an external network. Check redirect, secure cookie, HSTS, expiry alert and renewal path. Confirm actual firewall exposure and the intended DNS records.
- [ ] Create the intended admin organization. Verify sign-in, an employee invitation, assignment to exactly one project, denial in a sibling project and a scoped SDK evaluation. Verify the local model on this host if offering AI drafts.
- [ ] For a V9 upgrade, review all memberships. OWNER and ADMIN become ADMIN. Existing DEVELOPER and VIEWER become unassigned EMPLOYEE. Assign a project individually after review. Previous sessions must sign in again.
- [ ] Review pending invitations when offboarding admins. Invitations belong to the organization and expire after 48 hours. Cancel any outstanding grants that should expire immediately.
- [ ] Exercise expected login, refresh and SDK throughput on staging. Record target rates, latency and acceptable failures. Decide how to handle open organization registration and the shared gateway rate limits.
- [ ] Configure external health/latency, disk, memory, database, certificate and backup-age alerts. Exercise alert delivery and identify the on-call contact. Docker health state alone does not restart a live but unhealthy process.
- [ ] Agree on a recovery point and recovery time with the owner. Complete an encrypted off-host restore into a separate host, measure recovery time, and verify private key retrieval. Sign off before customer traffic.

The API health check performs a database connection check. The web probe loads the built index. The edge probe reads Caddy's local admin API. An external monitor should separately request the public console and `/healthz`. The Node gateway keeps sessions in memory. Restarting it prompts a new sign-in. API-only restarts preserve valid credentials with the same database and signing key. Service logs rotate locally at 10 MB with five files per container. Configure any exporter to omit request bodies, headers, invitation fragments, model briefs and SQL bind parameters.

## Backup and restore

Run the following with the deployment environment exported. `backup.sh` uses PostgreSQL 17's consistent custom-format logical dump, checks the archive listing, and publishes a SHA-256 manifest. Capture **every** release's pre-migration backup after draining writers. Schedule recurring backups at the interval required by the agreed recovery point. A daily schedule alone can lose almost a day of writes. Use managed PostgreSQL backups or WAL archiving if the agreed objective requires point-in-time recovery. [PostgreSQL restore options](https://www.postgresql.org/docs/17/app-pgrestore.html)

```bash
set -euo pipefail
bundle=$(infra/ops/backup.sh "$RELEASE_DIR/backups")
(cd "$bundle" && shasum -a 256 -c SHA256SUMS)
# Import and verify the recovery public key first. Set its fingerprint and the approved SSH destination.
archive="$RELEASE_DIR/$(basename "$bundle").tar.gpg"
tar -C "$bundle" -cf - SHA256SUMS database.dump | gpg --batch --encrypt --recipient "$BACKUP_RECIPIENT_FINGERPRINT" --output "$archive"
scp "$archive" "$BACKUP_HOST:backups/"
```

Configure GnuPG with the trusted recipient public key and configure SSH for that destination before scheduling this sequence. Alert on failures and stale off-host copies. Retain the pre-release artifact for the rollback window. Set encrypted storage retention and deletion permissions to the organization's requirements. A dump contains organization data, account password hashes and credential digests. PostgreSQL cluster roles, secrets, TLS keys and release images require separate recovery.

On the recovery host, unlock the separately protected GnuPG decryption key. Restore only a trusted archive from an authenticated backup location. Use a fresh candidate database name. The script verifies the checksum, refuses a pre-existing database, restores in one transaction using the application role, and prints the Flyway version. A failed restore can leave an unused database to inspect.

```bash
umask 077
recovered=$(mktemp -d)
gpg --decrypt "$archive" | tar -xf - -C "$recovered"
target="switchly_restore_$(date -u +%Y%m%d%H%M%S)"
infra/ops/restore.sh "$recovered" "$target"
docker compose exec -T postgres psql -XAt -U postgres -d "$target" \
  -c "SELECT count(*) FROM organization_memberships"
```

Before serving a recovered snapshot, reconcile deleted memberships, cancelled invitations, revoked refresh tokens and SDK keys since the backup. Recovery can make previously revoked credentials usable again. If the revocation history is unavailable, keep ingress closed, invalidate affected credentials, issue replacements and redistribute them through their owners. Verify known organization/project assignments, current admin access, targeting and SDK results on the recovered database.

## Deploy an update or roll back

For a first deployment with no previous live release, withdraw public traffic, stop the edge and application writers, and preserve the database and logs for diagnosis. Resume the previous traffic routing or maintenance response. The image/database pairing below applies when a known previous release exists.

1. Save the current release environment, **both** image identities and its deployment files. Ensure the previous image artifacts can be loaded. Announce a maintenance window and drain ingress, web and API with `docker compose stop edge web api`. This single-host deployment has downtime for migration.
2. With PostgreSQL running, take the pre-migration backup above. Preserve its path and digest with the old image IDs. Change the deployment environment to the candidate image tags. Re-export it with `set -a; . "$RELEASE_DIR/deployment.env"; set +a`. Run the first-start commands and the checklist.
3. On unexpected migration, authorization or data errors, stop ingress and application writers again. Capture the failed database in a **new** backup. Retain both database states for reconciliation of intervening writes.
4. If the previous build supports the live schema, restore its saved environment and images and recreate the application. For V10 → V9 or uncertain compatibility, restore the **matching pre-upgrade dump** to a fresh database with `restore.sh` and the previous PostgreSQL-compatible tooling. The current helper can restore an older database, and prints that database's Flyway version. Pair it with the previous build. Never start an older binary against a database with incompatible role values.
5. Set `SWITCHLY_DATABASE` in the saved release environment to the restored name, and confirm the resolved JDBC URL targets it. Re-export the environment. Run `docker compose up -d --wait --wait-timeout 150 api web`. Verify Flyway, tenant isolation and evaluation on the restored release, then `docker compose up -d --wait edge`. Record the cutover time, verification outcome and any writes requiring recovery. Retain the failed database. A switchback uses the same steps with the original database name and its matching images.

For routine shutdown, use `docker compose stop` or `docker compose down` to retain volumes. The console drains active requests on SIGTERM with a 100-second Compose grace period. The API uses Spring graceful shutdown with a 20-second phase timeout and a 35-second container grace period.
