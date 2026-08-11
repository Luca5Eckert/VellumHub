FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY services/gateway-service/pom.xml services/gateway-service/pom.xml
RUN mvn -B -ntp -f services/gateway-service/pom.xml dependency:go-offline

COPY services/gateway-service/src services/gateway-service/src
RUN mvn -B -ntp -f services/gateway-service/pom.xml package -DskipTests

ARG OTEL_JAVA_AGENT_VERSION=2.28.1
RUN mvn -B -ntp -f services/gateway-service/pom.xml dependency:copy \
    -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:${OTEL_JAVA_AGENT_VERSION}:jar \
    -DoutputDirectory=/otel \
    -Dmdep.stripVersion=true \
    -Dtransitive=false

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S vellumhub && adduser -S -u 10001 -G vellumhub vellumhub

WORKDIR /app

COPY --from=build --chown=vellumhub:vellumhub /workspace/services/gateway-service/target/*.jar app.jar
COPY --from=build --chown=vellumhub:vellumhub /otel/opentelemetry-javaagent.jar opentelemetry-javaagent.jar

USER vellumhub

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]