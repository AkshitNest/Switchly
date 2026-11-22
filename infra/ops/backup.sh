#!/usr/bin/env bash
set -euo pipefail
umask 077
[[ $# == 1 ]] || { echo "Usage: $0 BACKUP_DIRECTORY" >&2; exit 2; }
mkdir -p "$1"
# Reserve an exclusive path. An interrupted job never publishes a partial dump.
bundle=$(mktemp -d "$1/switchly-$(date -u +%Y%m%dT%H%M%SZ).XXXXXX")
database=${SWITCHLY_DATABASE:-switchly}
docker compose exec -T postgres sh -ec 'exec pg_dump -U "$POSTGRES_USER" -d "$1" --format=custom --no-owner --no-acl' sh "$database" > "$bundle/database.partial"
docker compose exec -T postgres pg_restore --list < "$bundle/database.partial" > /dev/null
mv "$bundle/database.partial" "$bundle/database.dump"
(cd "$bundle" && shasum -a 256 database.dump > SHA256SUMS)
printf '%s\n' "$bundle"
