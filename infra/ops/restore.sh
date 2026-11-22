#!/usr/bin/env bash
set -euo pipefail
[[ $# == 2 ]] || { echo "Usage: $0 BACKUP_DIRECTORY NEW_DATABASE" >&2; exit 2; }
bundle=$1
target=$2
[[ $target =~ ^[a-z][a-z0-9_]{1,62}$ ]] || { echo "Use a lowercase database name" >&2; exit 2; }
(cd "$bundle" && shasum -a 256 -c SHA256SUMS >&2)
# createdb fails if the name already exists. Never restore over an existing database.
docker compose exec -T postgres sh -ec 'exec createdb -U "$POSTGRES_USER" --template template0 "$1"' sh "$target"
docker compose exec -T postgres sh -ec 'exec psql -X -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1"' sh "$target" <<'SQL'
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', current_database()) \gexec
SELECT format('GRANT CONNECT, TEMPORARY ON DATABASE %I TO switchly', current_database()) \gexec
GRANT USAGE, CREATE ON SCHEMA public TO switchly;
SQL
docker compose exec -T postgres sh -ec 'exec pg_restore --single-transaction --exit-on-error --no-owner --no-acl --role=switchly -U "$POSTGRES_USER" --dbname "$1"' sh "$target" < "$bundle/database.dump"
docker compose exec -T postgres sh -ec 'exec psql -XAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1"' sh "$target" <<'SQL'
SELECT version,success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;
SQL
printf 'Restored into %s. The running API still uses its configured database.\n' "$target"
