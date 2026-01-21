#!/usr/bin/env python3
"""
Verify E2E Test Data

This script checks if the required test data exists in the database.
"""

import psycopg2
import os
import sys

# Configuration
POSTGRES_USER = os.getenv("POSTGRES_USER", "admin")
POSTGRES_PASSWORD = os.getenv("POSTGRES_PASSWORD", "admin123")
POSTGRES_HOST = os.getenv("POSTGRES_HOST", "localhost")
POSTGRES_PORT = os.getenv("POSTGRES_PORT", "5432")

def check_users():
    """Check if test users exist"""
    print("Checking users in user_db...")
    
    try:
        conn = psycopg2.connect(
            host=POSTGRES_HOST,
            port=POSTGRES_PORT,
            database="user_db",
            user=POSTGRES_USER,
            password=POSTGRES_PASSWORD
        )
        
        cur = conn.cursor()
        cur.execute("SELECT email, role FROM users WHERE email IN ('admin@e2e.test', 'teste@exemplo.com')")
        users = cur.fetchall()
        
        if len(users) == 2:
            print("  ✓ Both test users exist:")
            for email, role in users:
                print(f"    - {email} ({role})")
            return True
        else:
            print(f"  ✗ Only {len(users)}/2 test users found")
            for email, role in users:
                print(f"    - {email} ({role})")
            return False
            
    except Exception as e:
        print(f"  ✗ Error: {e}")
        return False
    finally:
        if 'cur' in locals():
            cur.close()
        if 'conn' in locals():
            conn.close()

def check_media():
    """Check if test media exists"""
    print("Checking media in catalog_db...")
    
    try:
        conn = psycopg2.connect(
            host=POSTGRES_HOST,
            port=POSTGRES_PORT,
            database="catalog_db",
            user=POSTGRES_USER,
            password=POSTGRES_PASSWORD
        )
        
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) FROM media WHERE id LIKE 'media-action%' OR id LIKE 'media-thriller%'")
        count = cur.fetchone()[0]
        
        if count >= 10:
            print(f"  ✓ {count} test media items found")
            
            # Check genres
            cur.execute("""
                SELECT genres, COUNT(*) 
                FROM media_genres 
                WHERE media_id LIKE 'media-action%' OR media_id LIKE 'media-thriller%'
                GROUP BY genres
            """)
            genres = cur.fetchall()
            print("    Genres:")
            for genre, count in genres:
                print(f"      - {genre}: {count} items")
            
            return True
        else:
            print(f"  ✗ Only {count}/10 test media items found")
            return False
            
    except Exception as e:
        print(f"  ✗ Error: {e}")
        return False
    finally:
        if 'cur' in locals():
            cur.close()
        if 'conn' in locals():
            conn.close()

def main():
    print("=" * 70)
    print("  E2E Test Data Verification")
    print("=" * 70)
    print()
    
    users_ok = check_users()
    print()
    media_ok = check_media()
    print()
    
    if users_ok and media_ok:
        print("✓ All test data is ready!")
        print()
        print("You can now run the E2E test:")
        print("  python3 scripts/e2e_test.py")
        print("  OR")
        print("  ./scripts/run_e2e_test.sh")
        return 0
    else:
        print("✗ Test data is incomplete")
        print()
        print("Run the seed script:")
        print(f"  docker exec -i media-db psql -U {POSTGRES_USER} < scripts/seed-e2e-data.sql")
        print("  OR")
        print("  pip3 install psycopg2-binary && python3 scripts/seed_e2e_python.py")
        return 1

if __name__ == '__main__':
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\n\nInterrupted")
        sys.exit(1)
    except psycopg2.OperationalError as e:
        print(f"\n✗ Database connection failed: {e}")
        print("\nMake sure PostgreSQL is running:")
        print("  docker-compose ps media-db")
        sys.exit(1)
