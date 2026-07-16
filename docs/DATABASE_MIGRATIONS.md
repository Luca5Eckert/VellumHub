# Database migrations

Issue: https://github.com/Luca5Eckert/VellumHub/issues/205

The PostgreSQL schemas for `user-service`, `catalog-service`, `engagement-service`, and `recommendation-service` are managed by Flyway.

## Runtime flow

- Production profiles run Flyway before Hibernate starts.
- Production profiles use `spring.jpa.hibernate.ddl-auto=validate`.
- H2 local/test profiles keep `ddl-auto=create-drop` and disable Flyway because the migrations use PostgreSQL-specific SQL.
- Existing non-empty schemas without a Flyway history table are baselined on first migration. Empty databases still run `V1__create_initial_schema.sql`.

## Adding a migration

Create a new versioned SQL file in the service that owns the database:

```text
<service>/src/main/resources/db/migration/V2__describe_change.sql
```

Keep migrations append-only after they are shared. For schema changes, update the JPA model and add the next Flyway version in the same change. Do not return production profiles to `ddl-auto=update`.

## Docker Compose

The PostgreSQL `init.sql` files no longer create application schema. Compose creates the empty databases, then each service applies its own migrations during startup. The recommendation database still uses the pgvector image, and the `vector` extension is created by the recommendation migration.

## Runtime verification

Each database-owning service has a Testcontainers integration test that starts an empty PostgreSQL database with the `prod` profile. The test verifies that Flyway applies the schema, Hibernate validates it, and an incompatible schema prevents a second application startup. The recommendation test uses `pgvector/pgvector:pg15` and also verifies the `vector` extension and HNSW indexes.

The services consume the local `kafka-contracts` artifact, so install it before running service tests independently:

```powershell
.\services\catalog-service\mvnw.cmd -f .\lib\kafka-contracts\pom.xml install

foreach ($service in 'catalog-service','user-service','engagement-service','recommendation-service') {
    Push-Location "services\$service"
    .\mvnw.cmd test
    Pop-Location
}
```

Docker must be available to run the Testcontainers tests. To validate the same behavior through Compose without reusing existing volumes, run the stack with an isolated project name and inspect the four service healthchecks:

```powershell
docker compose -p vellumhub-migrations-test up -d --build
docker compose -p vellumhub-migrations-test ps
docker compose -p vellumhub-migrations-test down -v
```
