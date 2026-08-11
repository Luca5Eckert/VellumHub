FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY lib/kafka-contracts/pom.xml lib/kafka-contracts/pom.xml
COPY lib/kafka-contracts/src lib/kafka-contracts/src
COPY services/recommendation-service/pom.xml services/recommendation-service/pom.xml

RUN mvn -B -ntp -f lib/kafka-contracts/pom.xml install -DskipTests \
    && mvn -B -ntp -f services/recommendation-service/pom.xml dependency:go-offline

COPY services/recommendation-service/src services/recommendation-service/src
RUN mvn -B -ntp -f services/recommendation-service/pom.xml package -DskipTests

ARG OTEL_JAVA_AGENT_VERSION=2.28.1
RUN mvn -B -ntp -f services/recommendation-service/pom.xml dependency:copy \
    -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:${OTEL_JAVA_AGENT_VERSION}:jar \
    -DoutputDirectory=/otel \
    -Dmdep.stripVersion=true \
    -Dtransitive=false

# Debian is retained because the embedded embedding model requires libstdc++ at runtime.
FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends libstdc++6 wget \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 vellumhub \
    && useradd --system --uid 10001 --gid vellumhub --home-dir /app --shell /usr/sbin/nologin vellumhub

WORKDIR /app

COPY --from=build --chown=vellumhub:vellumhub /workspace/services/recommendation-service/target/*.jar app.jar
COPY --from=build --chown=vellumhub:vellumhub /otel/opentelemetry-javaagent.jar opentelemetry-javaagent.jar

USER vellumhub

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]