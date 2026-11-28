"""Exercise PKCE, Gateway reads and a preference write using staging HTTPS."""
import json
from pathlib import Path
import secrets
import sys
from urllib.request import Request
from uuid import UUID

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "infra/scripts/security"))
import verify_oidc_lote2_flow as baseline

code, verifier, _ = baseline.oauth.create_authorization_code(
    login_first=True, scopes="openid user:read user:write")
with baseline.oauth.exchange(code, verifier) as response:
    assert response.status == 200
    bearer = json.load(response)["access_token"]
trace_id = secrets.token_hex(16)
parent_id = secrets.token_hex(8)
request = Request(
    baseline.gateway + "/api/v1/users/me/preferences",
    data=json.dumps({"genres": ["Fantasy"]}).encode(),
    headers={"Authorization": "Bearer " + bearer, "Content-Type": "application/json",
             "traceparent": f"00-{trace_id}-{parent_id}-01"},
    method="PUT",
)
with baseline.api_client.open(request, timeout=20) as response:
    assert response.status == 200
    assert json.load(response)["genres"] == ["Fantasy"]
user_id = UUID(baseline.oauth.jwt_parts(bearer)[1]["sub"])
Path(sys.argv[1]).write_text(f"{user_id} {trace_id}\n")
