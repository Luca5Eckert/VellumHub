# syntax=docker/dockerfile:1.7
# Every deployable service builds independently. Keep this matrix in sync with
# compose.yaml and .github/workflows/verify-images.yml.
ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
ARG DEPS_STAGE=deps-avro
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine

FROM ${MAVEN_IMAGE} AS deps-jwt
WORKDIR /workspace
COPY pom.xml pom.xml
RUN mvn -B -ntp -f pom.xml install -N -Dmaven.test.skip=true
COPY lib/jwt-security/pom.xml lib/jwt-security/pom.xml
COPY lib/jwt-security/src lib/jwt-security/src
RUN mvn -B -ntp -f lib/jwt-security/pom.xml install -Dmaven.test.skip=true

FROM deps-jwt AS deps-kafka
COPY lib/kafka-contracts/pom.xml lib/kafka-contracts/pom.xml
COPY lib/kafka-contracts/src lib/kafka-contracts/src
RUN mvn -B -ntp -f lib/kafka-contracts/pom.xml install -Dmaven.test.skip=true

FROM deps-kafka AS deps-avro
COPY lib/kafka-avro-contracts/pom.xml lib/kafka-avro-contracts/pom.xml
COPY lib/kafka-avro-contracts/src lib/kafka-avro-contracts/src
RUN mvn -B -ntp -f lib/kafka-avro-contracts/pom.xml install -Dmaven.test.skip=true

FROM ${DEPS_STAGE} AS build
ARG SERVICE
ARG OTEL_JAVA_AGENT_VERSION=2.28.1
COPY services/${SERVICE}/pom.xml services/${SERVICE}/pom.xml
RUN mvn -B -ntp -f services/${SERVICE}/pom.xml dependency:go-offline -DproductionImageBuild=true
COPY services/${SERVICE}/src services/${SERVICE}/src
RUN mvn -B -ntp -f services/${SERVICE}/pom.xml package -DproductionImageBuild=true -Dmaven.test.skip=true
# Keeping one copy of the OTel Java agent in all images is intentional:
# Auth doesn't enable it by default; the other five services do.
RUN mvn -B -ntp -f services/${SERVICE}/pom.xml dependency:copy \
    -DproductionImageBuild=true \
    -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:${OTEL_JAVA_AGENT_VERSION}:jar \
    -DoutputDirectory=/otel -Dmdep.stripVersion=true -Dtransitive=false

# Recommendation requires the Debian image with libstdc++ for its local model.
# All other services use Alpine. Do not silently drop this exception.
FROM ${RUNTIME_IMAGE} AS runtime
ARG SERVICE
ARG APP_PORT=8080
ARG HEALTH_PORT=8080
RUN set -eux; \
    if command -v apk >/dev/null 2>&1; then \
        addgroup -S vellumhub && adduser -S -u 10001 -G vellumhub vellumhub; \
    else \
        apt-get update && apt-get install -y --no-install-recommends libstdc++6 wget \
        && rm -rf /var/lib/apt/lists/* \
        && groupadd --system --gid 10001 vellumhub \
        && useradd --system --uid 10001 --gid vellumhub --home-dir /app --shell /usr/sbin/nologin vellumhub; \
    fi
WORKDIR /app
COPY --from=build --chown=vellumhub:vellumhub /workspace/services/${SERVICE}/target/*.jar app.jar
COPY --from=build --chown=vellumhub:vellumhub /otel/opentelemetry-javaagent.jar opentelemetry-javaagent.jar
USER vellumhub
ENV HEALTH_PORT=${HEALTH_PORT}
EXPOSE ${APP_PORT}
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${HEALTH_PORT}/actuator/health/liveness" || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
