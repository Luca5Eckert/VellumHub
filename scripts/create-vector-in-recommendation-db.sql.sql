-- Habilita a extensão de vetores
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE book_features (
    book_id UUID PRIMARY KEY,
    embedding vector(15),
    popularity_score FLOAT DEFAULT 0.0,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Índice HNSW para busca por similaridade de cosseno
CREATE INDEX idx_book_embedding_hnsw ON book_features
USING hnsw (embedding vector_cosine_ops);