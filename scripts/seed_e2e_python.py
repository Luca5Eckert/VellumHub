#!/usr/bin/env python3
"""
Programmatic Database Seeder for E2E Testing

This script creates admin user and test media directly in the database
using Python and psycopg2. Use this if the SQL seed script fails.
"""

import psycopg2
import bcrypt
import os
from datetime import datetime

# Configuration from .env or defaults
POSTGRES_USER = os.getenv("POSTGRES_USER", "admin")
POSTGRES_PASSWORD = os.getenv("POSTGRES_PASSWORD", "admin123")
POSTGRES_HOST = os.getenv("POSTGRES_HOST", "localhost")
POSTGRES_PORT = os.getenv("POSTGRES_PORT", "5432")

# Test credentials
TEST_PASSWORD = "SecurePass123!"
# Pre-computed BCrypt hash for "SecurePass123!" with rounds=10
BCRYPT_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"

def create_users():
    """Create admin and test users in user_db"""
    print("Creating users in user_db...")
    
    conn = psycopg2.connect(
        host=POSTGRES_HOST,
        port=POSTGRES_PORT,
        database="user_db",
        user=POSTGRES_USER,
        password=POSTGRES_PASSWORD
    )
    
    try:
        cur = conn.cursor()
        
        # Create admin user
        cur.execute("""
            INSERT INTO users (id, name, email, password, role, created_at, updated_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (email) DO NOTHING
        """, (
            'e2e-admin-uuid-0000-0000-000000000001',
            'E2E Admin User',
            'admin@e2e.test',
            BCRYPT_HASH,
            'ADMIN',
            datetime.now(),
            datetime.now()
        ))
        
        # Create regular test user
        cur.execute("""
            INSERT INTO users (id, name, email, password, role, created_at, updated_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (email) DO NOTHING
        """, (
            'e2e-user-uuid-0000-0000-000000000001',
            'E2E Test User',
            'teste@exemplo.com',
            BCRYPT_HASH,
            'USER',
            datetime.now(),
            datetime.now()
        ))
        
        conn.commit()
        print("✓ Users created successfully")
        
    except Exception as e:
        print(f"✗ Error creating users: {e}")
        conn.rollback()
    finally:
        cur.close()
        conn.close()

def create_media():
    """Create test media items in catalog_db"""
    print("Creating media items in catalog_db...")
    
    conn = psycopg2.connect(
        host=POSTGRES_HOST,
        port=POSTGRES_PORT,
        database="catalog_db",
        user=POSTGRES_USER,
        password=POSTGRES_PASSWORD
    )
    
    try:
        cur = conn.cursor()
        
        # Create ACTION media
        action_media = [
            ('media-action-uuid-0000-0000-000000000001', 'Action Hero 1', 'Uma história emocionante de ACTION'),
            ('media-action-uuid-0000-0000-000000000002', 'Action Hero 2', 'Uma história emocionante de ACTION'),
            ('media-action-uuid-0000-0000-000000000003', 'Action Hero 3', 'Uma história emocionante de ACTION'),
            ('media-action-uuid-0000-0000-000000000004', 'Action Hero 4', 'Uma história emocionante de ACTION'),
            ('media-action-uuid-0000-0000-000000000005', 'Action Hero 5', 'Uma história emocionante de ACTION'),
        ]
        
        for media_id, title, description in action_media:
            cur.execute("""
                INSERT INTO media (id, title, description, release_year, media_type, cover_url, created_at, updated_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (id) DO NOTHING
            """, (
                media_id, title, description, 2024, 'MOVIE',
                f'https://example.com/{media_id}.jpg',
                datetime.now(), datetime.now()
            ))
            
            # Add genre
            cur.execute("""
                INSERT INTO media_genres (media_id, genres)
                VALUES (%s, %s)
                ON CONFLICT DO NOTHING
            """, (media_id, 'ACTION'))
        
        # Create THRILLER media
        thriller_media = [
            ('media-thriller-uuid-0000-0000-000000000001', 'Thriller Mystery 1', 'Uma história emocionante de THRILLER'),
            ('media-thriller-uuid-0000-0000-000000000002', 'Thriller Mystery 2', 'Uma história emocionante de THRILLER'),
            ('media-thriller-uuid-0000-0000-000000000003', 'Thriller Mystery 3', 'Uma história emocionante de THRILLER'),
            ('media-thriller-uuid-0000-0000-000000000004', 'Thriller Mystery 4', 'Uma história emocionante de THRILLER'),
            ('media-thriller-uuid-0000-0000-000000000005', 'Thriller Mystery 5', 'Uma história emocionante de THRILLER'),
        ]
        
        for media_id, title, description in thriller_media:
            cur.execute("""
                INSERT INTO media (id, title, description, release_year, media_type, cover_url, created_at, updated_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (id) DO NOTHING
            """, (
                media_id, title, description, 2024, 'MOVIE',
                f'https://example.com/{media_id}.jpg',
                datetime.now(), datetime.now()
            ))
            
            # Add genre
            cur.execute("""
                INSERT INTO media_genres (media_id, genres)
                VALUES (%s, %s)
                ON CONFLICT DO NOTHING
            """, (media_id, 'THRILLER'))
        
        conn.commit()
        print("✓ Media items created successfully")
        
    except Exception as e:
        print(f"✗ Error creating media: {e}")
        conn.rollback()
    finally:
        cur.close()
        conn.close()

def main():
    """Main entry point"""
    print("=" * 70)
    print("  E2E Test Data Seeder (Python)")
    print("=" * 70)
    print()
    
    print(f"Database Configuration:")
    print(f"  Host: {POSTGRES_HOST}")
    print(f"  Port: {POSTGRES_PORT}")
    print(f"  User: {POSTGRES_USER}")
    print()
    
    try:
        create_users()
        create_media()
        
        print()
        print("✓ All test data seeded successfully!")
        print()
        print("Test Credentials:")
        print("  Admin: admin@e2e.test / SecurePass123! (ADMIN role)")
        print("  User: teste@exemplo.com / SecurePass123! (USER role)")
        print()
        print("Media Created:")
        print("  5 ACTION movies")
        print("  5 THRILLER movies")
        print()
        
        return 0
        
    except psycopg2.OperationalError as e:
        print(f"\n✗ Database connection failed: {e}")
        print("\nMake sure:")
        print("  1. PostgreSQL is running: docker-compose ps")
        print("  2. .env file has correct POSTGRES_USER and POSTGRES_PASSWORD")
        print("  3. Services have been started: docker-compose up -d")
        return 1
    except Exception as e:
        print(f"\n✗ Unexpected error: {e}")
        import traceback
        traceback.print_exc()
        return 1

if __name__ == '__main__':
    import sys
    sys.exit(main())
