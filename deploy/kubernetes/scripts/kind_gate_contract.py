"""Pure, unit-testable contract for in-cluster OAuth/business E2E Job results.

Successful pod exit alone is insufficient: both original OAuth harness and
real persistent-write/negative-scope assertions must print their exact marker.
"""
OAUTH_MARKER = (
    "Real Auth UUIDv7 -> User profile -> Gateway -> "
    "User/Catalog/Engagement/Recommendation and negative JWT checks: OK"
)
JOURNEY_MARKER = (
    "Kind 3 distinct business journeys: OAuth and cross-domain reads; "
    "catalog persist/read/delete; insufficient-scope mutation denied: OK"
)
EXPECTED_FAILURE = "Gateway->User did not accept token HTTP"


def inspect_business_job(status, logs, *, expect_gateway_failure=False):
    """Return classified result or None while running; reject false successes."""
    if status.get("succeeded", 0):
        if expect_gateway_failure:
            raise RuntimeError("Deliberately broken Gateway passed business E2E")
        if OAUTH_MARKER not in logs or JOURNEY_MARKER not in logs:
            raise RuntimeError("E2E exited successfully but required scenario assertions are missing")
        return {
            "status": "PASSED",
            "real_oauth_pkce": True,
            "real_auth_to_user_provisioning": True,
            "real_gateway_domains_checked": [
                "user", "catalog", "engagement", "recommendation"
            ],
            "business_scenarios": [
                "authenticated cross-domain reads",
                "catalog persistence roundtrip",
                "insufficient-scope mutation denied"
            ],
            "negative": [
                "reduced scope denied", "ID token denied",
                "malformed JWT denied", "ADMIN mutation denied"
            ],
            "ci_fixture": "ephemeral accounts, not Google live or production"
        }
    if status.get("failed", 0):
        if expect_gateway_failure and EXPECTED_FAILURE in logs:
            return {
                "status": "EXPECTED_REGRESSION_DETECTED",
                "failed_assertion": "authenticated Gateway->User profile HTTP",
                "health_check_alone_would_be_insufficient": True
            }
        raise RuntimeError("Unexpected business E2E failure: " + logs[-850:])
    return None
