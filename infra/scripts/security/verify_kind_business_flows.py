#!/usr/bin/env python3
"""Independent Kind business scenarios using ephemeral identities and real services.

The imported baseline harness exercises authorization-code + PKCE, reads across
four Java services and JWT negative cases. This script additionally proves that
a user-owned resource can be persisted, read, denied with insufficient scope,
and deleted through the real Gateway + Catalog + PostgreSQL path.
"""
import json
import secrets

import verify_oidc_lote2_flow as base

# Real business journey 2: user-owned list write -> persistence -> read -> delete.
scopes = ("openid profile user:read catalog:read catalog:personal:read "
          "catalog:personal:write engagement:read recommendation:read")
code, verifier, _nonce = base.oauth.create_authorization_code(
    login_first=True, scopes=scopes)
response = base.oauth.exchange(code, verifier)
assert response.status == 200, f"Write-scoped OAuth code exchange HTTP {response.status}"
write_token = json.load(response)["access_token"]

name = "ci-" + secrets.token_hex(7)
payload = json.dumps({
    "title": name, "description": "Isolated Kind persistent-write check",
    "type": "PRIVATE", "booksId": []
}).encode("utf-8")
route = "/api/v1/catalog/book-list"

def write_request(method, path, token, payload=None):
    headers = {"Authorization": "Bearer " + token, "Accept": "application/json"}
    if payload is not None:
        headers["Content-Type"] = "application/json"
    request = base.urllib.request.Request(
        base.gateway + path, data=payload, headers=headers, method=method)
    try:
        return base.api_client.open(request, timeout=20)
    except base.urllib.error.HTTPError as error:
        return error

# Real business journey 3: deny a mutation without catalog:personal:write.
denied = write_request("POST", route, base.access, payload)
assert denied.status == 403, (
    f"Insufficient-scope list mutation returned {denied.status}, expected 403")

created = write_request("POST", route, write_token, payload)
assert created.status == 201, f"Authorized list create HTTP {created.status}"
resource_id = json.load(created)["id"]
assert resource_id, "Created list has no identifier"
try:
    fetched = write_request("GET", f"{route}/{resource_id}", write_token)
    assert fetched.status == 200, f"Persisted list GET HTTP {fetched.status}"
    assert json.load(fetched)["id"] == resource_id, "List identity changed across write/read"
finally:
    removed = write_request("DELETE", f"{route}/{resource_id}", write_token)
    assert removed.status == 204, f"Ephemeral list cleanup HTTP {removed.status}"

print("Kind 3 distinct business journeys: OAuth and cross-domain reads; catalog persist/read/delete; insufficient-scope mutation denied: OK")
