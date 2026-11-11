#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
for pidfile in .runtime/*.pid; do
 [ -e "$pidfile" ] || continue
 pid="$(cat "$pidfile")"
 # Verify the process still runs this service's JAR before stopping a possibly recycled PID.
 service="$(basename "$pidfile" .pid)"
 if ps -p "$pid" -o args= 2>/dev/null | grep -F "backend/$service/target/$service-1.0.0-SNAPSHOT.jar" >/dev/null; then kill "$pid"; fi
 rm -f "$pidfile"
done
