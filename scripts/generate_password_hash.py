#!/usr/bin/env python3
"""
Generate BCrypt password hashes for E2E test users
"""

import bcrypt

def generate_hash(password: str) -> str:
    """Generate BCrypt hash for a password"""
    salt = bcrypt.gensalt(rounds=10)
    hashed = bcrypt.hashpw(password.encode('utf-8'), salt)
    return hashed.decode('utf-8')

if __name__ == '__main__':
    password = "SecurePass123!"
    hash_value = generate_hash(password)
    print(f"Password: {password}")
    print(f"BCrypt Hash: {hash_value}")
    print(f"\nVerification: {bcrypt.checkpw(password.encode('utf-8'), hash_value.encode('utf-8'))}")
