#!/usr/bin/env bash
# Inspect the actual ranking SQL and HNSW access path against a synthetic catalog.
set -euo pipefail
cd "$(dirname "$0")/../.."
output=$(realpath -m "${1:?Provide a new report file}")
( set -o noclobber; : > "$output" )
service=services/recommendation-service
image=pgvector/pgvector:pg15
container=$(docker run -d --rm --network none -e POSTGRES_HOST_AUTH_METHOD=trust "$image")
trap 'docker rm -f "$container" >/dev/null' EXIT
for attempt in $(seq 1 30); do
  if docker exec "$container" pg_isready -h 127.0.0.1 -U postgres >/dev/null; then break; fi
  sleep 1
done
{
  printf 'sha=%s utc=%s image_id=%s\n' "$(git rev-parse HEAD)" "$(date -u +%FT%TZ)" \
    "$(docker inspect --format '{{.Image}}' "$container")"
  {
    while IFS= read -r migration; do
      cat "$migration"
      printf '\n'
    done < <(printf '%s\n' "$service"/src/main/resources/db/migration/V*__*.sql | sort -V)
    cat <<'SQL'
SELECT version();
SELECT extversion FROM pg_extension WHERE extname = 'vector';
SELECT setseed(0.42);
INSERT INTO book_features (book_id, embedding, popularity_score, last_updated)
SELECT md5(id::text)::uuid, ARRAY(
  SELECT (random() + (i = (id % 384 + 1))::int)::real FROM generate_series(1, 384) AS i
)::vector, random(), now()
FROM generate_series(1, 6000) AS id;
INSERT INTO user_profiles (
  user_id, profile_vector, interacted_book_ids, total_engagement_score, last_updated, created_at
)
SELECT '00000000-0000-4000-8000-000000000091', embedding, '{}', 1, now(), now()
FROM book_features WHERE book_id = md5('192')::uuid;
ANALYZE;
\echo 'Production ranking, three executions:'
SQL
    sql=$(python3 - "$service" <<'PY'
from pathlib import Path
import re
import sys

(root,) = Path(sys.argv[1], "src/main/java").rglob("RankingQueries.java")
source = root.read_text()
parts = []
for constant in ("CANDIDATES", "SEMANTIC_POPULARITY"):
    (code,) = re.findall(r'\b' + constant + r'\s*=\s*(?:CANDIDATES\s*\+\s*)?"""(.*?)"""', source, re.S)
    parts.append(code)
print(" ".join(parts).replace(":userId", "'00000000-0000-4000-8000-000000000091'")
      .replace(":limit", "10").replace(":offset", "0"))
PY
)
    for _ in 1 2 3; do
      printf 'EXPLAIN (ANALYZE, BUFFERS) %s;\n' "$sql"
    done
    cat <<'SQL'
\echo 'HNSW control, one profile with one distance sort key:'
EXPLAIN (ANALYZE, BUFFERS)
SELECT book_id FROM book_features
ORDER BY embedding <=> (SELECT profile_vector FROM user_profiles LIMIT 1)
LIMIT 10;
CREATE TEMP TABLE approximate AS
SELECT book_id FROM book_features
ORDER BY embedding <=> (SELECT profile_vector FROM user_profiles LIMIT 1)
LIMIT 10;
\echo 'Same control with index scans disabled:'
SET enable_indexscan = off;
EXPLAIN (ANALYZE, BUFFERS)
SELECT book_id FROM book_features
ORDER BY embedding <=> (SELECT profile_vector FROM user_profiles LIMIT 1)
LIMIT 10;
WITH exact AS (
  SELECT book_id FROM book_features
  ORDER BY embedding <=> (SELECT profile_vector FROM user_profiles LIMIT 1)
  LIMIT 10
)
SELECT count(*) AS overlap_at_10, count(*) / 10.0 AS recall_at_10
FROM approximate JOIN exact USING (book_id);
SQL
  } | docker exec -i "$container" psql -h 127.0.0.1 -U postgres -v ON_ERROR_STOP=1
} > "$output"
