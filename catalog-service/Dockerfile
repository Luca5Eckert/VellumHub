# --- STAGE 1: Build ---
# Usamos a tag oficial do Maven baseada no Eclipse Temurin 21.
# '3.9' garante que pegaremos a versão estável mais recente da linha 3.9.x
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY . .

# Compila o projeto
RUN mvn clean package -DskipTests

# --- STAGE 2: Runtime ---
# Mantemos o runtime também no Eclipse Temurin para compatibilidade total
# A versão alpine é mais leve para produção
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]