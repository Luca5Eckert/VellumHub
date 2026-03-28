CREATE TABLE IF NOT EXISTS genres (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

INSERT INTO genres (name)
VALUES
    ('Ficção Científica'),
    ('Fantasia'),
    ('Terror'),
    ('Romance'),
    ('Suspense'),
    ('Biografia'),
    ('História'),
    ('Autoajuda'),
    ('Distopia'),
    ('Aventura')
ON CONFLICT (name) DO NOTHING;