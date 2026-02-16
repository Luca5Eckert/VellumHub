-- Executar no banco do Recommendation Service
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE media_features (
    media_id UUID PRIMARY KEY,
    embedding vector(15),
    popularity_score FLOAT DEFAULT 0.0,
    last_updated TIMESTAMP NOT NULL
);

-- Índice HNSW para busca ultrarrápida e paginação estável
CREATE INDEX idx_media_vector_search ON media_features
USING hnsw (embedding vector_cosine_ops);