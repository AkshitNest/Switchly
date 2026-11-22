#!/bin/sh
set -eu
# The official image runs this once, on an empty cluster.
psql -X --set ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<'SQL'
CREATE ROLE switchly LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
REVOKE ALL ON DATABASE switchly FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE switchly TO switchly;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO switchly;
SQL
# psql's password command computes the SCRAM verifier on the client.
password=$(cat /run/secrets/db-password)
printf '%s\n%s\n' "$password" "$password" |
  psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c '\password switchly'
