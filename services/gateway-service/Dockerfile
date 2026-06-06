# STAGE 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# ✅ OTIMIZAÇÃO: Copiar apenas pom.xml primeiro (cache de dependências)
COPY pom.xml .

# ✅ Baixar dependências separadamente (será cacheado)
RUN mvn dependency:go-offline -B

# ✅ Agora copiar o código fonte
COPY src ./src

# ✅ Compilar o projeto (mais rápido porque deps já estão baixadas)
RUN mvn clean package -DskipTests -B

ARG OTEL_JAVA_AGENT_VERSION=2.28.1
RUN mvn dependency:copy \
  -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:${OTEL_JAVA_AGENT_VERSION}:jar \
  -DoutputDirectory=/otel \
  -Dmdep.stripVersion=true \
  -Dtransitive=false \
  -B

# STAGE 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# ✅ Copiar apenas o JAR compilado
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /otel/opentelemetry-javaagent.jar opentelemetry-javaagent.jar

# ✅ Adicionar healthcheck
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
USER nobody
