# STAGE 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# ✅ OTIMIZAÇÃO: Copiar apenas pom.xml primeiro (cache de dependências)
COPY lib/kafka-contracts/pom.xml ./lib/kafka-contracts/pom.xml
COPY services/user-service/pom.xml ./services/user-service/pom.xml

# ✅ Baixar dependências separadamente (será cacheado)
COPY lib/kafka-contracts/src ./lib/kafka-contracts/src
RUN mvn -f lib/kafka-contracts/pom.xml install -DskipTests -B
RUN mvn -f services/user-service/pom.xml dependency:go-offline -B

# ✅ Agora copiar o código fonte
COPY services/user-service/src ./services/user-service/src

# ✅ Compilar o projeto (mais rápido porque deps já estão baixadas)
RUN mvn -f services/user-service/pom.xml clean package -DskipTests -B

ARG OTEL_JAVA_AGENT_VERSION=2.28.1
RUN mvn -f services/user-service/pom.xml dependency:copy \
  -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:${OTEL_JAVA_AGENT_VERSION}:jar \
  -DoutputDirectory=/otel \
  -Dmdep.stripVersion=true \
  -Dtransitive=false \
  -B

# STAGE 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# ✅ Copiar apenas o JAR compilado
COPY --from=build /app/services/user-service/target/*.jar app.jar
COPY --from=build /otel/opentelemetry-javaagent.jar opentelemetry-javaagent.jar

# ✅ Adicionar healthcheck
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
