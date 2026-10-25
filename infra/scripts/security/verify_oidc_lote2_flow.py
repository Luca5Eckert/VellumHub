#!/usr/bin/env python3
"""SEC-06/09: live Auth -> User -> Gateway -> Catalog smoke, local Compose only.
Uses the real OAuth protocol harness and refuses to interpret an ID Token as bearer.
Requires all services from the docker-compose.oidc-lote2.yml overlay to be started.
"""
import json
import os
import secrets
import subprocess
import urllib.parse
import urllib.request

# Configure new registration credentials BEFORE importing the reusable PKCE client.
LOGIN = "oidc-" + secrets.token_hex(9) + "@example.invalid"
PASSWORD = secrets.token_urlsafe(30)
os.environ["VELLUM_POC_LOGIN"] = LOGIN
os.environ["VELLUM_POC_PASSWORD"] = PASSWORD
import verify_auth_poc_flow as oauth

gateway = os.environ.get("VELLUM_GATEWAY_URL", "http://127.0.0.1:8080")
body = json.dumps({
    "name": "OIDC Integration User",
    "email": LOGIN,
    "password": PASSWORD
}).encode()
created = oauth.fetch(oauth.ISSUER + "/v1/identity/register", data=body,
        headers={"Content-Type":"application/json", "Accept":"application/json"})
assert created.status == 201, (
    f"Auth->User provisioning failed: HTTP {created.status}; "
    f"final_url={created.geturl()}; "
    f"content_type={created.headers.get('Content-Type', '<missing>')}"
)
canonical_id = json.load(created)["userId"]

code, verifier, nonce = oauth.create_authorization_code(login_first=True)
result = oauth.exchange(code, verifier)
assert result.status == 200, f"OIDC token failed HTTP {result.status}"
tokens = json.load(result)
assert "refresh_token" not in tokens
access = tokens["access_token"]
header, claims = oauth.jwt_parts(access)
assert claims["sub"] == canonical_id
assert header["typ"] == "at+jwt"

def get(path, bearer):
    return oauth.fetch(gateway + path, headers={"Authorization": "Bearer " + bearer,
                                               "Accept":"application/json"})

# Identity must traverse Gateway and be loaded from User's autonomous database.
profile = get("/api/v1/users/me", access)
assert profile.status == 200, f"Gateway->User did not accept token HTTP {profile.status}"
assert json.load(profile)["id"] == canonical_id

# A protected read must succeed and a USER cannot mutate ADMIN-only Catalog content.
books = get("/api/v1/catalog/books", access)
assert books.status == 200, f"Gateway->Catalog JWT validation failed HTTP {books.status}"
# Use a valid mutation payload: an empty object fails Bean Validation with 400
# before @PreAuthorize can demonstrate the USER -> ADMIN boundary.
valid_book = json.dumps({
    "title": "OIDC Security Smoke",
    "description": "Authorization check only",
    "releaseYear": 2026,
    "coverUrl": "https://example.invalid/cover.jpg",
    "author": "CI Runner",
    "isbn": "9780132350884",
    "pageCount": 150,
    "publisher": "VellumHub CI",
    "genres": ["FICTION"]
}).encode()
denied = oauth.fetch(gateway + "/api/v1/catalog/books", data=valid_book,
        headers={"Authorization": "Bearer " + access, "Content-Type": "application/json"})
assert denied.status == 403, (
    f"Valid Catalog ADMIN-only request returned HTTP {denied.status}, expected 403"
)
id_token = get("/api/v1/users/me", tokens["id_token"])
assert id_token.status == 401, "Gateway accepted an ID Token as bearer"
malformed = get("/api/v1/users/me", "not-a-jwt")
assert malformed.status == 401, "Gateway accepted malformed JWT"
print("Real Auth UUIDv7 -> User persisted profile -> Gateway relay -> Catalog read/403 and ID Token rejection: OK")

if os.environ.get("VELLUM_VERIFY_OFFLINE") == "1":
    cmd = ["docker", "compose", "-f", "docker-compose.yml", "-f", "docker-compose.oidc-lote2.yml"]
    subprocess.run(cmd + ["stop", "auth-service"], check=True)
    try:
        cached = get("/api/v1/users/me", access)
        assert cached.status == 200, f"Warm JWKS JWT failed when AS offline: HTTP {cached.status}"
        print("Auth offline, Gateway + User cached JWT: OK")
    finally:
        subprocess.run(cmd + ["start", "auth-service"], check=True)
