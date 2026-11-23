#!/usr/bin/env bash
# Check the actual Catalog search SQL without building the Java reactor.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../../.."
migration=services/catalog-service/src/main/resources/db/migration
export POSTGRES_PASSWORD
POSTGRES_PASSWORD=$(python3 -c 'import secrets; print(secrets.token_urlsafe(24))')
db=$(docker run --detach --network none -e POSTGRES_PASSWORD postgres:15-alpine)
trap 'docker rm -fv "$db" >/dev/null' EXIT

for i in {1..30}; do
    if docker exec "$db" pg_isready -h 127.0.0.1 -U postgres >/dev/null; then
        break
    fi
    sleep 1
done
docker exec "$db" pg_isready -h 127.0.0.1 -U postgres >/dev/null
psql_in_fixture() {
    docker exec -i "$db" psql -X -v ON_ERROR_STOP=1 -U postgres "$@"
}

cat "$migration"/V[1-6]__*.sql | psql_in_fixture >/dev/null
psql_in_fixture >/dev/null <<'SQL'
INSERT INTO books(id,title,author,description,release_year,isbn,page_count,publisher,version,created_at,deleted_at)
VALUES
('00000000-0000-0000-0000-000000000001','Nebulosa','A. Silva','Uma expedição',2025,'fixture-1',150,'Fixture',0,now(),NULL),
('00000000-0000-0000-0000-000000000002','Uma expedição','B. Costa','Viagem por uma nebulosa',2025,'fixture-2',150,'Fixture',0,now(),NULL),
('00000000-0000-0000-0000-000000000003','Nebulosa','C. Reis',NULL,2025,'fixture-3',150,'Fixture',0,now(),now());
INSERT INTO books(id,title,author,release_year,isbn,page_count,publisher,version,created_at)
SELECT md5('fixture-' || n)::uuid, 'Livro de teste ' || n, 'Autor', 2025, 'filler-' || n, 200, 'Editora', 0, now()
FROM generate_series(1,6000) AS n;
SQL
psql_in_fixture < "$migration"/V7__index_book_text_search.sql >/dev/null
psql_in_fixture -c 'ANALYZE books' >/dev/null

python3 - "$db" <<'PYCODE'
from pathlib import Path
import re
import subprocess
import sys

repository = next(Path("services/catalog-service/src/main/java").rglob("JpaBookRepository.java"))
sql = re.search(r'@Query\(value = """\s*(SELECT.*?)\s*""", nativeQuery = true\)', repository.read_text(), re.S)
assert sql, "Unable to read the native Catalog search query"
query = sql.group(1).replace(":text", "$1").replace(":limit", "$2").replace(":offset", "$3")
prepare = f"PREPARE search_books(text, integer, bigint) AS SELECT id FROM ({query}) match;"

def search(term, offset=0, explain=False):
    command = [
        "docker", "exec", "-i", sys.argv[1], "psql", "-XqAt", "-U", "postgres",
        "-v", "ON_ERROR_STOP=1", "-v", f"term={term}", "-v", f"offset={offset}",
    ]
    execute = "EXECUTE search_books(:'term', 1, :'offset');"
    if explain:
        execute = "EXPLAIN " + execute
    return subprocess.check_output(command, input=f"{prepare}\n{execute}\n", text=True).strip()

assert search("NEBULOSA") == "00000000-0000-0000-0000-000000000001"
assert search("nebulosa", offset=1) == "00000000-0000-0000-0000-000000000002"
assert not search("nebulosa", offset=2), "Deleted book surfaced in search results"
assert search("Silva") == "00000000-0000-0000-0000-000000000001"
assert not search("sem correspondência")
assert not search("!!!")
plan = search("nebulosa", explain=True)
assert "idx_books_visible_text_search" in plan, plan
print("PostgreSQL 15: V1–V7, ranking, paging, soft delete, zero matches and GIN plan verified.")
PYCODE
