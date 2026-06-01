CREATE TABLE IF NOT EXISTS genres (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

INSERT INTO genres (name)
VALUES
    (U&'Fic\00E7\00E3o Cient\00EDfica'),
    ('Fantasia'),
    ('Terror'),
    ('Romance'),
    ('Suspense'),
    ('Biografia'),
    (U&'Hist\00F3ria'),
    ('Autoajuda'),
    ('Distopia'),
    ('Aventura')
ON CONFLICT (name) DO NOTHING;
