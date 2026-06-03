CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE book_features (
    book_id UUID PRIMARY KEY,
    embedding vector(384),
    popularity_score DOUBLE PRECISION NOT NULL,
    last_updated TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE user_profiles (
    user_id UUID PRIMARY KEY,
    profile_vector vector(384),
    interacted_book_ids UUID[],
    total_engagement_score DOUBLE PRECISION NOT NULL,
    last_updated TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE recommendations (
    book_id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    release_year INTEGER NOT NULL,
    cover_url VARCHAR(255) NOT NULL,
    author VARCHAR(255) NOT NULL
);

CREATE TABLE recommendation_genres (
    recommendation_book_id UUID NOT NULL,
    genres VARCHAR(255),
    CONSTRAINT fk_recommendation_genres_recommendation_book_id
        FOREIGN KEY (recommendation_book_id) REFERENCES recommendations (book_id)
);

CREATE INDEX idx_book_features_embedding_hnsw
    ON book_features
    USING hnsw (embedding vector_cosine_ops);

CREATE INDEX idx_user_profiles_profile_vector_hnsw
    ON user_profiles
    USING hnsw (profile_vector vector_cosine_ops);
