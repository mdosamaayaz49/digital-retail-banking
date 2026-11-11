#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ -e .env ]; then echo '.env already exists; preserving it.'; exit 0; fi
umask 077
{ printf 'DATABASE_PASSWORD=%s\n' "$(openssl rand -hex 24)"; printf 'KEYCLOAK_ADMIN_PASSWORD=%s\n' "$(openssl rand -hex 24)"; printf 'DEMO_PASSWORD=%s\n' "$(openssl rand -hex 16)"; } > .env
printf 'Created private .env. Demo users: amira, daniel, reviewer. Read DEMO_PASSWORD locally.\n'
