#!/usr/bin/env python3
"""Real local OAuth Authorization Code + PKCE S256 smoke (stdlib only).
This is a browser-like protocol harness, not a production OIDC client.
"""
from __future__ import annotations

import base64
import hashlib
import http.cookiejar
from html.parser import HTMLParser
import json
import os
import secrets
import urllib.error
import urllib.parse
import urllib.request
import uuid

ISSUER = os.environ.get("VELLUM_AUTH_ISSUER", "http://127.0.0.1:9000")
CLIENT = "vellumhub-spa-poc"
REDIRECT = "http://127.0.0.1:9100/index.html"
LOGIN = os.environ["VELLUM_POC_LOGIN"]
PASSWORD = os.environ["VELLUM_POC_PASSWORD"]


class StopAtCallback(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if newurl.startswith(REDIRECT):
            return None
        return super().redirect_request(req, fp, code, msg, headers, newurl)


class HiddenCsrf(HTMLParser):
    def __init__(self):
        super().__init__()
        self.token = None

    def handle_starttag(self, tag, attrs):
        if tag == "input":
            values = dict(attrs)
            if values.get("name") == "_csrf":
                self.token = values.get("value")


cookies = http.cookiejar.CookieJar()
client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies), StopAtCallback())


def fetch(url, data=None, headers=None):
    request = urllib.request.Request(url, data=data, headers=headers or {})
    try:
        return client.open(request, timeout=15)
    except urllib.error.HTTPError as error:
        return error


def encoded(value):
    return base64.urlsafe_b64encode(value).decode("ascii").rstrip("=")


def random_token():
    return encoded(secrets.token_bytes(32))


def create_authorization_code(verifier=None, method="S256", login_first=False):
    verifier = verifier or random_token()
    challenge = encoded(hashlib.sha256(verifier.encode("ascii")).digest()) if method == "S256" else verifier
    state, nonce = random_token(), random_token()
    query = urllib.parse.urlencode({
        "response_type": "code",
        "client_id": CLIENT,
        "redirect_uri": REDIRECT,
        "scope": "openid profile",
        "state": state,
        "nonce": nonce,
        "code_challenge": challenge,
        "code_challenge_method": method
    })
    response = fetch(ISSUER + "/oauth2/authorize?" + query, headers={"Accept": "text/html"})
    if method != "S256":
        assert response.status == 400, f"plain challenge accepted or wrong response: {response.status}"
        return None, verifier, nonce
    if response.status == 200 and login_first:
        csrf = HiddenCsrf()
        csrf.feed(response.read().decode("utf-8"))
        assert csrf.token, "Login form lacks CSRF token"
        body = urllib.parse.urlencode({
            "username": LOGIN,
            "password": PASSWORD,
            "_csrf": csrf.token
        }).encode()
        response = fetch(ISSUER + "/login", data=body,
                         headers={"Content-Type": "application/x-www-form-urlencoded"})
    assert response.status == 302, f"Expected OAuth callback redirect, got HTTP {response.status}"
    redirect = response.headers.get("Location", "")
    assert redirect.startswith(REDIRECT), f"Unexpected redirect destination: {redirect!r}"
    result = urllib.parse.parse_qs(urllib.parse.urlsplit(redirect).query)
    assert result.get("state") == [state], "OAuth state mismatch"
    assert "error" not in result, result
    assert len(result.get("code", [])) == 1, "No authorization code returned"
    return result["code"][0], verifier, nonce


def exchange(code, verifier):
    body = urllib.parse.urlencode({
        "grant_type": "authorization_code",
        "client_id": CLIENT,
        "redirect_uri": REDIRECT,
        "code": code,
        "code_verifier": verifier
    }).encode()
    return fetch(ISSUER + "/oauth2/token", data=body,
                 headers={"Content-Type": "application/x-www-form-urlencoded",
                          "Accept": "application/json"})


def jwt_parts(compact):
    header, payload, _signature = compact.split(".")
    def unpack(value):
        return json.loads(base64.urlsafe_b64decode(value + "=" * (-len(value) % 4)))
    return unpack(header), unpack(payload)


def run():
    discovery = json.load(fetch(ISSUER + "/.well-known/openid-configuration"))
    assert discovery["issuer"] == ISSUER
    assert discovery["jwks_uri"] == ISSUER + "/oauth2/jwks"
    keys = json.load(fetch(discovery["jwks_uri"]))
    assert keys["keys"], "JWKS has no keys"
    for key in keys["keys"]:
        assert "d" not in key, "Private key exposed in JWKS"

    _invalid, _verifier, _nonce = create_authorization_code(method="plain")
    code, verifier, nonce = create_authorization_code(login_first=True)
    ok = exchange(code, verifier)
    assert ok.status == 200, f"Token exchange failed: HTTP {ok.status}, {ok.read()[:600]!r}"
    tokens = json.load(ok)
    assert "refresh_token" not in tokens
    assert tokens["token_type"].lower() == "bearer"
    assert tokens["expires_in"] <= 600
    header, claims = jwt_parts(tokens["access_token"])
    assert header["alg"] == "RS256" and header["typ"] == "at+jwt"
    assert header["kid"] in {key["kid"] for key in keys["keys"]}
    assert claims["iss"] == ISSUER
    assert "api://vellumhub" in claims["aud"]
    assert claims["client_id"] == CLIENT
    assert uuid.UUID(claims["sub"]).version == 7
    assert claims["jti"] and claims["iat"] < claims["exp"]

    _, id_claims = jwt_parts(tokens["id_token"])
    assert id_claims["nonce"] == nonce
    assert id_claims["sub"] == claims["sub"]
    assert id_claims["aud"] == CLIENT

    api = fetch(ISSUER + "/poc/api/me", headers={
        "Authorization": "Bearer " + tokens["access_token"],
        "Accept": "application/json"})
    assert api.status == 200, f"Protected API denied valid token: {api.status}, {api.read()[:600]!r}"
    assert json.load(api)["sub"] == claims["sub"]

    id_as_access = fetch(ISSUER + "/poc/api/me", headers={
        "Authorization": "Bearer " + tokens["id_token"],
        "Accept": "application/json"})
    assert id_as_access.status == 401, "API incorrectly accepted OIDC ID Token"
    assert exchange(code, verifier).status == 400, "Authorization code reuse accepted"

    second, second_verifier, _nonce = create_authorization_code()
    assert exchange(second, random_token()).status == 400, "Wrong PKCE verifier accepted"
    print("Real OIDC + PKCE S256, UUIDv7/RFC9068, JWKS, protected API and negative tests: OK")


if __name__ == "__main__":
    run()
