CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Cria a tabela caso o Hibernate ainda não tenha subido
CREATE TABLE IF NOT EXISTS tb_users (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- Insere o Admin Master com hash bcrypt
-- NOTE: Default password should be changed on first login
INSERT INTO tb_users (id, name, email, password, role, active, version, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'Admin Master',
    'admin@mrs.com',
    '$2a$10$8.Vqhas32IdzIyay6jJFhuz99bshH6z7Xf.9A9V0YpPzK.FvMbaW6',
    'ADMIN',
    true,
    1,
    NOW(),
    NOW()
) ON CONFLICT (email) DO UPDATE SET role = 'ADMIN', active = true;