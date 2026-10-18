#!/usr/bin/env python3
"""Exercise real RS256 issuance and verification across the running Compose stack.

Uses a new ephemeral account and never prints passwords or bearer tokens.
Requires the Compose services and databases to be ready.
"""
import base64
import hashlib
import hmac
import json
import os
import secrets
import time
import urllib.error
import urllib.request


GATEWAY = os.environ.get("RS256_SMOKE_GATEWAY_URL", "http://localhost:8080").rstrip("/")


def http(method, path, body=None, bearer=None):
    headers = {"Accept": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if bearer is not None:
        headers["Authorization"] = "Bearer " + bearer
    request = urllib.request.Request(
        GATEWAY + path, data=data, headers=headers, method=method
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        return error.code, ""  # Do not echo server error bodies or credentials.


def expect_status(status, expected, scenario):
    if status != expected:
        raise AssertionError(
            f"{scenario}: expected HTTP {expected}, received HTTP {status}"
        )


def b64url_encode(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def b64url_decode(data):
    return base64.urlsafe_b64decode(data + "=" * (-len(data) % 4))


def main():
    email = f"rs256-smoke-{secrets.token_hex(12)}@example.test"
    password = secrets.token_urlsafe(24)
    status, _ = http("POST", "/api/v1/auth/register", {
        "name": "RS256 CI Smoke", "email": email, "password": password,
    })
    expect_status(status, 201, "Public registration")

    status, response = http("POST", "/api/v1/auth/login", {
        "email": email, "password": password,
    })
    expect_status(status, 200, "Public login")
    token = response.strip().strip('"')
    parts = token.split(".")
    if len(parts) != 3:
        raise AssertionError("Login did not return a compact signed JWT")

    header = json.loads(b64url_decode(parts[0]))
    payload = json.loads(b64url_decode(parts[1]))
    if header.get("alg") != "RS256":
        raise AssertionError("User Service did not emit RS256")
    if payload.get("sub") != email or not payload.get("user_id"):
        raise AssertionError("Login JWT lost its expected identity claims")
    if "ROLE_USER" not in payload.get("roles", []):
        raise AssertionError("Login JWT lost its USER role")
    if payload.get("iat", 0) > time.time() or payload.get("exp", 0) <= time.time():
        raise AssertionError("Login JWT has invalid temporal claims")

    paths = (
        "/api/v1/users/me",
        "/api/v1/catalog/books?pageNumber=0&pageSize=10",
        "/api/v1/engagement/rating/me?pageNumber=0&pageSize=10",
    )
    for path in paths:
        status, _ = http("GET", path, bearer=token)
        expect_status(status, 200, "RS256 authorized request to " + path)

    status, _ = http("GET", paths[0])
    expect_status(status, 401, "Missing bearer JWT")

    tampered = dict(payload)
    tampered["sub"] = "tampered@example.test"
    modified = parts[0] + "." + b64url_encode(
        json.dumps(tampered, separators=(",", ":")).encode("utf-8")
    ) + "." + parts[2]
    status, _ = http("GET", paths[0], bearer=modified)
    expect_status(status, 401, "Tampered RS256 signature")

    legacy_header = b64url_encode(b'{"alg":"HS256","typ":"JWT"}')
    legacy_payload = b64url_encode(
        json.dumps(payload, separators=(",", ":")).encode("utf-8")
    )
    signed_input = (legacy_header + "." + legacy_payload).encode("ascii")
    mac = hmac.new(secrets.token_bytes(32), signed_input, hashlib.sha256).digest()
    legacy_token = signed_input.decode("ascii") + "." + b64url_encode(mac)
    status, _ = http("GET", paths[0], bearer=legacy_token)
    expect_status(status, 401, "Legacy HS256 bearer JWT")

    print("RS256 Compose smoke passed: issuance, 3 service paths, and negative JWTs.")


if __name__ == "__main__":
    main()
