# Offline Recommendation Quality Evaluation

This harness compares the current personalized ranker with explicit ranking baselines using the same deterministic text dataset, user profiles, holdout relevance set, exclusions, embedding pipeline, and eligible candidate pool.

## What is measured

Rankers:

1. `popularity-only`;
2. `semantic-only`;
3. `semantic-popularity` — the current production scoring query (`70%` semantic distance + `30%` popularity).

Primary metric: `nDCG@10`.

Required metrics:

- Precision@10;
- Recall@10;
- nDCG@10;
- MRR@10.

The summary also reports catalog coverage and empty-result rate.

## Dataset v2: production-model embeddings

`vellumhub-synthetic-v2-model-embedded` is generated deterministically with seed `2822026`.

- 120 books;
- 24 users;
- 6 broad preference clusters;
- 2 semantic subtypes per cluster;
- 5 profile/training interactions per user;
- 5 holdout relevant items per user;
- complementary train/holdout splits for the two users representing each preference;
- deterministic but deliberately uncorrelated popularity values.

The dataset stores **textual book metadata, not embedding vectors**. Each book has title, author, description, genres, popularity, and the hidden preference label used only to define the evaluation ground truth.

During the reference run, all 120 book vectors are generated from that textual metadata by the same production path used by the recommendation-service:

1. `AllMiniLmL6V2EmbeddingModel` from LangChain4j produces the raw 384-dimensional embedding;
2. `LangChain4jEmbeddingBookProvider` builds the same semantic content from title, author, genres, and description and applies the same L2 normalization;
3. the resulting vectors are persisted in the real `book_features.embedding vector(384)` column;
4. user vectors are learned from the generated training-book embeddings through the existing `UserProfile.applyUpdate` logic;
5. pgvector/HNSW and the canonical production ranking SQL execute the comparison.

The text intentionally includes overlapping concepts across preference groups. Broad genres also overlap between subtypes (for example Fantasy + Adventure versus Fantasy + Mystery), so the benchmark cannot rely on a manually separated vector dimension to recover the ground truth.

The holdout set never participates in profile construction. The catalog is deliberately smaller than the production candidate pool of 200. After excluding the five profile interactions, every eligible book fits inside candidate generation. This means all three rankers see the same eligible items before final ordering.

### What v2 improves over v1

The original v1 harness hand-authored normalized 384-dimensional vectors with explicit cluster/subtype coordinates. That was useful for validating metric math, filtering, pgvector integration, and report generation, but it made semantic ranking artificially easy.

V2 removes those benchmark vectors completely. The quality result now includes the behavior of the actual embedding model and the production text-to-vector preprocessing path. The dataset is still synthetic, so the result remains an offline benchmark rather than production-impact evidence.

## Run

Docker must be available because the reference evaluation uses `pgvector/pgvector:pg15` through Testcontainers and applies the real Flyway schema.

From the repository root:

```bash
mvn -pl services/recommendation-service -am \
  -Dtest=RecommendationQualityEvaluationIT \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dgroups=evaluation \
  test
```

`failIfNoSpecifiedTests=false` is required because `-am` first builds `kafka-contracts`, which intentionally does not contain the evaluation test selected for the recommendation-service.

A reference GitHub Actions run passes the PR head commit SHA and output location explicitly. Results are written to:

```text
services/recommendation-service/target/evaluation-results/reference/
  run-metadata.json
  raw-results.json
  summary.md
```

`run-metadata.json` includes the concrete embedding model and provider class used by the run.

Regular unit/functional CI does not select `RecommendationQualityEvaluationIT`; deterministic metric, split, dataset, and serialization tests remain part of the normal test suite.

## Interpretation guardrails

- This is offline evaluation, not evidence of online user impact.
- The text corpus is synthetic and intentionally controlled; it is not production behavior.
- V2 exercises the production embedding implementation, but it still does not replace a public/real interaction benchmark or an online experiment.
- No quality threshold is used to make CI pass or fail.
- Do not tune the dataset after seeing results merely to make a ranker win.
- Do not call these values an SLA.
- Resume/portfolio claims must use only values from a versioned reference run and must identify them as reproducible offline evaluation when context requires it.
