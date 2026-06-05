# STAGE 1: Build (Keep this the same)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

ARG OTEL_JAVA_AGENT_VERSION=2.28.1
RUN mvn dependency:copy \
  -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:${OTEL_JAVA_AGENT_VERSION}:jar \
  -DoutputDirectory=/otel \
  -Dmdep.stripVersion=true \
  -Dtransitive=false \
  -B

FROM eclipse-temurin:21-jre

# Debian-based images don't need gcompat, but we ensure basic tools are there
RUN apt-get update && apt-get install -y --no-install-recommends \
    libstdc++6 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar
COPY --from=build /otel/opentelemetry-javaagent.jar opentelemetry-javaagent.jar

# Standard Spring Boot healthcheck
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
USER nobody
