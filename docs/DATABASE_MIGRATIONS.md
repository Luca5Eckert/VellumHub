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
