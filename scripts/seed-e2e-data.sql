-- Seed script for E2E testing
-- This script creates an admin user and test media for E2E testing
-- 
-- ⚠️  WARNING: FOR TESTING ONLY ⚠️
-- This script contains test credentials and should NEVER be used in production
-- The password hashes and data are intentionally simple for automated testing
-- 
-- Test Credentials (DO NOT USE IN PRODUCTION):
-- - admin@e2e.test / SecurePass123! (ADMIN role)
-- - teste@exemplo.com / SecurePass123! (USER role)

\c user_db

-- Create admin user for E2E testing
-- Password: SecurePass123! (BCrypt hash with rounds=10)
-- ⚠️ TEST-ONLY: This hash is for testing and publicly visible in source control
INSERT INTO users (id, name, email, password, role, created_at, updated_at)
VALUES (
    'e2e-admin-uuid-0000-0000-000000000001',
    'E2E Admin User',
    'admin@e2e.test',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',  -- BCrypt hash for "SecurePass123!"
    'ADMIN',
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- Create regular test user
-- ⚠️ TEST-ONLY: This hash is for testing and publicly visible in source control
INSERT INTO users (id, name, email, password, role, created_at, updated_at)
VALUES (
    'e2e-user-uuid-0000-0000-000000000001',
    'E2E Test User',
    'teste@exemplo.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',  -- BCrypt hash for "SecurePass123!"
    'USER',
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

\c catalog_db

-- Create test media items (5 ACTION, 5 THRILLER)
INSERT INTO media (id, title, description, release_year, media_type, cover_url, created_at, updated_at)
VALUES 
    ('media-action-uuid-0000-0000-000000000001', 'Action Hero 1', 'Uma história emocionante de ACTION', 2024, 'MOVIE', 'https://example.com/action-hero-1.jpg', NOW(), NOW()),
    ('media-action-uuid-0000-0000-000000000002', 'Action Hero 2', 'Uma história emocionante de ACTION', 2024, 'MOVIE', 'https://example.com/action-hero-2.jpg', NOW(), NOW()),
    ('media-action-uuid-0000-0000-000000000003', 'Action Hero 3', 'Uma história emocionante de ACTION', 2024, 'MOVIE', 'https://example.com/action-hero-3.jpg', NOW(), NOW()),
    ('media-action-uuid-0000-0000-000000000004', 'Action Hero 4', 'Uma história emocionante de ACTION', 2024, 'MOVIE', 'https://example.com/action-hero-4.jpg', NOW(), NOW()),
    ('media-action-uuid-0000-0000-000000000005', 'Action Hero 5', 'Uma história emocionante de ACTION', 2024, 'MOVIE', 'https://example.com/action-hero-5.jpg', NOW(), NOW()),
    ('media-thriller-uuid-0000-0000-000000000001', 'Thriller Mystery 1', 'Uma história emocionante de THRILLER', 2024, 'MOVIE', 'https://example.com/thriller-mystery-1.jpg', NOW(), NOW()),
    ('media-thriller-uuid-0000-0000-000000000002', 'Thriller Mystery 2', 'Uma história emocionante de THRILLER', 2024, 'MOVIE', 'https://example.com/thriller-mystery-2.jpg', NOW(), NOW()),
    ('media-thriller-uuid-0000-0000-000000000003', 'Thriller Mystery 3', 'Uma história emocionante de THRILLER', 2024, 'MOVIE', 'https://example.com/thriller-mystery-3.jpg', NOW(), NOW()),
    ('media-thriller-uuid-0000-0000-000000000004', 'Thriller Mystery 4', 'Uma história emocionante de THRILLER', 2024, 'MOVIE', 'https://example.com/thriller-mystery-4.jpg', NOW(), NOW()),
    ('media-thriller-uuid-0000-0000-000000000005', 'Thriller Mystery 5', 'Uma história emocionante de THRILLER', 2024, 'MOVIE', 'https://example.com/thriller-mystery-5.jpg', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Create media_genres junction table entries
INSERT INTO media_genres (media_id, genres)
VALUES 
    ('media-action-uuid-0000-0000-000000000001', 'ACTION'),
    ('media-action-uuid-0000-0000-000000000002', 'ACTION'),
    ('media-action-uuid-0000-0000-000000000003', 'ACTION'),
    ('media-action-uuid-0000-0000-000000000004', 'ACTION'),
    ('media-action-uuid-0000-0000-000000000005', 'ACTION'),
    ('media-thriller-uuid-0000-0000-000000000001', 'THRILLER'),
    ('media-thriller-uuid-0000-0000-000000000002', 'THRILLER'),
    ('media-thriller-uuid-0000-0000-000000000003', 'THRILLER'),
    ('media-thriller-uuid-0000-0000-000000000004', 'THRILLER'),
    ('media-thriller-uuid-0000-0000-000000000005', 'THRILLER')
ON CONFLICT DO NOTHING;

-- Seed data loaded successfully
-- Users created: admin@e2e.test (ADMIN), teste@exemplo.com (USER)
-- Media created: 5 ACTION, 5 THRILLER
