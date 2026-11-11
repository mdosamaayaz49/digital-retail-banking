#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
# Requires Java 21/Maven. Uses a random local broker port.
mvn -B -f backend/pom.xml -pl banking-kernel -am install -DskipTests
mvn -B -f backend/pom.xml -pl notification-service test-compile dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/kafka-classpath.txt
mkdir -p .runtime
exec java -Xms64m -Xmx256m -cp "backend/notification-service/target/test-classes:backend/notification-service/target/classes:$(cat backend/notification-service/target/kafka-classpath.txt)" com.nusantara.notifications.LocalKafkaBroker .runtime/kafka.bootstrap
