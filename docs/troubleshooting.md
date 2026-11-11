# Troubleshooting

- **JRE but no JDK:** `java -version` alone is insufficient. Verify `javac -version` is 21 and JAVA_HOME points to the JDK; Maven compilation otherwise fails.
- **Read-only home cache:** use `mvn -Dmaven.repo.local=/workspace/work/m2 ...` and `npm --cache /workspace/work/npm-cache ...`; keep caches outside tracked source.
- **Corporate/cloud proxy:** Maven does not automatically honor HTTP_PROXY. Configure a local untracked Maven settings.xml proxy; use the installed system Java CA trust store or import your approved proxy CA into a dedicated trust store. Never disable TLS verification. Containers need their build tools' CA trust configured too. Do not bake session trust files or credentials into images.
- **No disk space:** rootless Docker vfs stores copied layers. Inspect `df -h` and `docker system df`. Stop and remove only this project's disposable test containers/images, then retry; never broadly prune an unfamiliar shared Docker host. Allow additional room for Java/Node dependencies.
- **Keycloak not ready:** check realm discovery http://localhost:8180/realms/nusantara/.well-known/openid-configuration and `docker compose logs keycloak`. First realm import happens only when the realm does not already exist. Browser and JWT issuer must agree exactly on scheme/host/port.
- **Port 8180 identity from a remote browser:** localhost belongs to the browser machine. Use approved port forwarding or a configured hostname, update issuer, SPA identity URL, realm redirect/web origins and Nginx CSP together. Do not weaken issuer validation.
- **401:** inspect issuer/audience/expiry through safe developer tooling; never log a complete access token. The public client uses PKCE; password grant is deliberately disabled.
- **429 / Redis failure:** gateway denies requests without available limit capacity. Check Redis health and network. Do not bypass limits by exposing the core externally.
- **403 PROFILE_REQUIRED after registration:** open Profile & settings and save a fictional profile; new identities do not automatically receive funds/accounts. Use seeded identities for transfers.
- **Kafka unavailable:** transfers can still commit while outbox retries. Check `published_at`, attempts and logs; restart the broker without resubmitting a new financial transfer key. A repeated published event is safe for the notification primary key.
- **PDF permissions:** the requesting customer must own both the metadata and account. Statements use Kuala Lumpur calendar boundaries.
- **Testcontainers Docker API:** use a supported Docker client/API level; the managed Docker 29 engine may require `DOCKER_API_VERSION=1.44`. Test databases must be isolated. Only in constrained ephemeral environments use `TESTCONTAINERS_RYUK_DISABLED=true`, then remove your test containers explicitly.

- **ClassNotFoundException after rebuilding a running JAR:** stop the Java process before replacing its executable archive, then start the newly packaged JAR. Boot loads nested classes lazily; overwriting the live artifact can invalidate archive offsets.

- **JDBC-only service requires entityManagerFactory:** exclude both HibernateJpaAutoConfiguration and JpaRepositoriesAutoConfiguration. The completed customer/statement/notification service entry points do this; do not remove either exclusion.
- **Native Kafka random port:** EmbeddedKafkaKraftBroker chooses an ephemeral port. Use .runtime/kafka.bootstrap via scripts/run-local.sh; setting kafkaPorts(9092) is not reliable in KRaft mode. Do not hardcode the broker's previous port.
- **MFA test says invalid code after enrollment:** supported Keycloak TOTP policy may reject reuse in the same time window. The test waits for a new code before the second login, preserving the provider's replay protection.
- **New identity cannot log in using short username:** the demo realm uses email-as-username for new registrations. Use the fictional email; legacy imported demo usernames remain available.
- **Date filters omit the first Malaysian hours:** dates must be cast to timestamp before AT TIME ZONE. The completed queries and midnight boundary tests cover this conversion.
- **STATEMENT_TOO_LARGE:** choose a narrower date range. PDF generation is bounded to 5,000 ledger entries and never silently truncates balances.
