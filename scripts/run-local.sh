#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
[ -f .env ] || sh scripts/init-env.sh
set -a
. ./.env
set +a
mkdir -p .runtime
if [ -z "${KAFKA_BOOTSTRAP:-}" ] && [ -s .runtime/kafka.bootstrap ]; then export KAFKA_BOOTSTRAP="$(cat .runtime/kafka.bootstrap)"; fi
# Build first; never overwrite executable JARs in running JVMs.
for name in transfer-service auth-service customer-service account-service statement-service notification-service api-gateway; do
  if [ -e ".runtime/$name.pid" ] && kill -0 "$(cat ".runtime/$name.pid")" 2>/dev/null; then echo "$name already running; stop it before rebuilding" >&2; exit 1; fi
done
mvn -B -f backend/pom.xml package -DskipTests
for name in transfer-service auth-service customer-service account-service statement-service notification-service api-gateway; do
  nohup java -Xms64m -Xmx256m -jar "backend/$name/target/$name-1.0.0-SNAPSHOT.jar" > ".runtime/$name.log" 2>&1 &
  echo "$!" > ".runtime/$name.pid"
  if [ "$name" = transfer-service ]; then
    retries=0
    until curl -fs http://localhost:8081/actuator/health/readiness >/dev/null; do
      retries=$((retries+1)); [ "$retries" -lt 60 ] || { echo 'Core failed readiness. See .runtime/transfer-service.log'; exit 1; }; sleep 1
    done
  fi
done
for port in 8080 8082 8083 8084 8085 8086; do
 retries=0
 until curl -fs "http://localhost:$port/actuator/health/readiness" >/dev/null; do
  retries=$((retries+1)); [ "$retries" -lt 90 ] || { echo "Service port $port failed readiness. See .runtime logs." >&2; sh scripts/stop-local.sh; exit 1; }; sleep 1
 done
done
printf 'Services ready. Run cd frontend && npm ci && npm run dev, then open http://localhost:5173\n'
