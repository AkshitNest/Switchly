#!/usr/bin/env bash
set -euo pipefail
[[ $# == 1 ]] || { echo "Usage: $0 NEW_PRIVATE_DIRECTORY" >&2; exit 2; }
umask 077
mkdir -m 700 "$1"
for name in jwt-key db-password bootstrap-password; do
  openssl rand -hex 32 | tr -d '\n' > "$1/$name"
  # The private parent protects host access. The granted container user can read its file mount.
  chmod 0444 "$1/$name"
done
