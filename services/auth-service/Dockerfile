FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY lib/jwt-security/pom.xml lib/jwt-security/pom.xml
COPY lib/jwt-security/src lib/jwt-security/src
COPY services/auth-service/pom.xml services/auth-service/pom.xml
RUN mvn -B -ntp -f lib/jwt-security/pom.xml install -DskipTests
RUN mvn -B -ntp -f services/auth-service/pom.xml dependency:go-offline
COPY services/auth-service/src services/auth-service/src
RUN mvn -B -ntp -f services/auth-service/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S vellumhub && adduser -S -u 10001 -G vellumhub vellumhub
WORKDIR /app
COPY --from=build --chown=vellumhub:vellumhub /workspace/services/auth-service/target/*.jar app.jar
USER vellumhub
EXPOSE 9000
HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:9000/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
