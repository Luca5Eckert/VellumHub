-- 1. Habilita a extensão pgvector (caso ainda não esteja habilitada)
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Criação da tabela
CREATE TABLE IF NOT EXISTS book_features (
    book_id UUID PRIMARY KEY,
    embedding vector(15),
    popularity_score DOUBLE PRECISION,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 3. Criação do índice (conforme seu snippet original)
CREATE INDEX IF NOT EXISTS idx_book_embedding_hnsw
ON book_features
USING hnsw (embedding vector_cosine_ops);