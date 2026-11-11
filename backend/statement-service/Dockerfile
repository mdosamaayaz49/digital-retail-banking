FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -B -pl statement-service -am package -DskipTests
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/statement-service/target/statement-service-1.0.0-SNAPSHOT.jar app.jar
RUN chmod -R g=u /app
USER 10001
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
