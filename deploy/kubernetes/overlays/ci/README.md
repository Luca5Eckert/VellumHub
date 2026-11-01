# Ephemeral integration-test overlay (non-production)

Wraps the actual local Kind overlay; does not introduce stand-in services.
Overrides the issuer to cluster-internal HTTP and enables the existing
authorization-code/PKCE test client **only in the disposable CI namespace**.
Sessions are HTTP-only here solely for the untrusted, self-contained Kind test.
Production and the local overlay do not inherit these overrides.

The full-stack CI workflow creates ephemeral RSA keys and PostgreSQL passwords.
The test Job executes the existing Auth→User→Gateway→Catalog/Engagement/
Recommendation OAuth2 integration harness against actual Java containers.
