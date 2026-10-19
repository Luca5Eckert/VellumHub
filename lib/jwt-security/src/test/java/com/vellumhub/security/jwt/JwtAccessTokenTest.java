package com.vellumhub.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAccessTokenTest {
    private static final String ISSUER = "https://auth.vellumhub.local";
    private static final String AUDIENCE = "api://vellumhub";
    private static final String USER_ID = "93bb05c3-48a4-43b4-b789-410782cb0c9f";

    @Test
    void acceptsOnlyWellFormedUuidBearerAccessTokens() {
        Jwt valid = jwt(ISSUER, List.of(AUDIENCE), USER_ID, "at+jwt", "RS256", USER_ID, true);
        assertThat(JwtAccessToken.validator(ISSUER, AUDIENCE, 30).validate(valid).hasErrors()).isFalse();
        assertThat(JwtAccessToken.userId(valid)).isEqualTo(UUID.fromString(USER_ID));
    }

    @Test
    void rejectsIssuerAudienceAlgorithmTypeSubjectAndLegacyClaimMismatch() {
        assertInvalid(jwt("https://issuer.invalid", List.of(AUDIENCE), USER_ID, "at+jwt", "RS256", USER_ID, true));
        assertInvalid(jwt(ISSUER, List.of("api://other"), USER_ID, "at+jwt", "RS256", USER_ID, true));
        assertInvalid(jwt(ISSUER, List.of(AUDIENCE), USER_ID, "JWT", "RS256", USER_ID, true));
        assertInvalid(jwt(ISSUER, List.of(AUDIENCE), USER_ID, "at+jwt", "HS256", USER_ID, true));
        assertInvalid(jwt(ISSUER, List.of(AUDIENCE), "someone@example.test", "at+jwt", "RS256", null, true));
        assertInvalid(jwt(ISSUER, List.of(AUDIENCE), USER_ID, "at+jwt", "RS256", UUID.randomUUID().toString(), true));
        assertInvalid(jwt(ISSUER, List.of(AUDIENCE), USER_ID, "at+jwt", "RS256", USER_ID, false));
    }

    @Test
    void enforcesConfigSanityAndDistinguishesAuthorities() {
        assertThatThrownBy(() -> JwtAccessToken.validator(ISSUER, AUDIENCE, 121))
                .isInstanceOf(IllegalArgumentException.class);
        Jwt jwt = Jwt.withTokenValue("fixture")
                .header("alg", "RS256").header("typ", "at+jwt")
                .subject(USER_ID)
                .claim("roles", List.of("USER"))
                .claim("scope", "books:read progress:write")
                .build();
        var authorities = JwtAccessToken.servletConverter().convert(jwt).getAuthorities();
        assertThat(authorities).extracting(Object::toString)
                .contains("ROLE_USER", "SCOPE_books:read", "SCOPE_progress:write");
    }

    private void assertInvalid(Jwt jwt) {
        assertThat(JwtAccessToken.validator(ISSUER, AUDIENCE, 30).validate(jwt).hasErrors()).isTrue();
    }

    private Jwt jwt(String issuer, List<String> audience, String subject, String typ,
                    String alg, String legacyUserId, boolean clientId) {
        Instant now = Instant.now();
        Jwt.Builder b = Jwt.withTokenValue("fixture")
                .header("alg", alg).header("typ", typ)
                .claim("iss", issuer).claim("aud", audience)
                .claim("sub", subject)
                .claim("iat", now.minusSeconds(10))
                .claim("exp", now.plusSeconds(120))
                .claim("jti", UUID.randomUUID().toString());
        if (legacyUserId != null) b.claim("user_id", legacyUserId);
        if (clientId) b.claim("client_id", "vellumhub-web");
        return b.build();
    }
}
