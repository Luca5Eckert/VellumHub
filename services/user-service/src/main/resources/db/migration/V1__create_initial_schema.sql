CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE tb_users (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    deleted_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT uk_tb_users_email UNIQUE (email)
);

CREATE TABLE user_preferences (
    id UUID PRIMARY KEY,
    user_id UUID,
    about VARCHAR(255),
    CONSTRAINT uk_user_preferences_user_id UNIQUE (user_id),
    CONSTRAINT fk_user_preferences_user_id
        FOREIGN KEY (user_id) REFERENCES tb_users (id)
);

CREATE TABLE user_preference_genres (
    preference_id UUID NOT NULL,
    genre_name VARCHAR(255),
    CONSTRAINT fk_user_preference_genres_preference_id
        FOREIGN KEY (preference_id) REFERENCES user_preferences (id)
);

INSERT INTO tb_users (id, name, email, password, role, active, version, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'Admin Master',
    'admin@mrs.com',
    '$2a$10$ArVoaRe2ih4.VVwrbrKUqORa2Kh7IfRa3I0Z5RBQGGJaNrkCjQBXG',
    'ADMIN',
    TRUE,
    1,
    NOW(),
    NOW()
);
