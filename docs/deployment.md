# Deployment

Compose is the supported local demonstration topology. PostgreSQL data has a named volume; Keycloak uses an ephemeral development store, Kafka is a single non-durable development broker, Redis limits are ephemeral. Do not expose this stack publicly.

Build backend Dockerfiles from `backend/`, e.g. `docker build -f backend/transfer-service/Dockerfile backend`. Frontend uses Node build + unprivileged Nginx. Java images run UID 10001 and writable directories permit an arbitrary OpenShift group. Maven unit/integration tests run in CI, outside image builds; `-DskipTests` only applies inside Docker packaging.

Kubernetes manifests are an application deployment **template**, not a provisioned cluster. Build/tag/push images to your own registry, replace image placeholders, provision PostgreSQL/Kafka/Redis/Keycloak separately with production operators, and create `banking-secrets` out of band. Example secret keys: `DATABASE_PASSWORD`; never commit a Secret with actual values. Apply ConfigMap and deployments after setting OIDC issuer/JWKS, database URL, Kafka bootstrap and CORS origin. Use TLS ingress on your chosen domain, rebuild frontend with corresponding `VITE_OIDC_URL`, and configure Keycloak redirect/web origins to match. Nginx's production CSP origins must also match the identity domain.

Readiness and liveness use Spring Boot `/actuator/health/readiness` and `/actuator/health/liveness`. These do not prove downstream business dependency health; add readiness groups and observability with care, avoiding restart cascades. Startup probes allow migrations before liveness. PostgreSQL migrations currently execute on banking startup; for multi-replica production use a controlled migration job and separate credentials.

OpenShift uses restricted SCC-compatible containers with arbitrary UID, no privilege escalation, dropped capabilities, RuntimeDefault seccomp and a read-only Java root filesystem with a writable /tmp emptyDir. Route template uses edge TLS; backend service traffic needs re-encrypt or service-mesh TLS for stronger in-cluster guarantees. Nginx image supports unprivileged execution but needs writable cache/run volumes in a restricted cluster.

Manifests and CI have not been deployed to a real Kubernetes/OpenShift cluster in the authoring session. There is no claim of HA, DR, production durability, encryption-at-rest, bank regulatory approval or load-testing capacity.

## Complete service topology

Compose now includes all seven service Dockerfiles. Gateway routes profile/account/statement/identity/recovery paths to their designated services; financial/admin/history paths go to the ledger owner. Core runs Flyway before dependent services start. JDBC-only services explicitly disable JPA repository/Hibernate auto-configuration. Never start them against an uninitialized schema.

For host development, run scripts/run-local.sh and scripts/stop-local.sh. The former refuses to overwrite JARs belonging to recorded active processes, builds, starts migration owner first, then waits for all readiness probes. PIDs/logs are in ignored .runtime. For constrained disks, scripts/kafka-local.sh starts a real test-library KRaft broker at a random port and writes .runtime/kafka.bootstrap for the local startup script. This is a development helper, not a production broker.

Optional monitoring: set a local GRAFANA_PASSWORD, then run `docker compose -f docker-compose.yml -f infrastructure/docker/compose-monitoring.yml up -d`. Prometheus is localhost:9090 and Grafana localhost:3000; its pre-provisioned datasource uses internal Prometheus. Service metrics include outbox pending, pending orders, quarantined notifications and standard JVM/HTTP metrics. Production requires durable monitoring storage, alert rules and access controls.

All Kubernetes application manifests passed strict kubeconform validation. The OpenShift Route was validated against the official openshift/api OpenAPI definition; it targets the frontend Service's named http port. This establishes schema validity, not cluster scheduling, ingress, external identity, network policy, TLS or secret provisioning. The workflow includes seven Java service image builds plus the frontend image build and a full authenticated E2E job; consult verification.md for observed remote execution status.
