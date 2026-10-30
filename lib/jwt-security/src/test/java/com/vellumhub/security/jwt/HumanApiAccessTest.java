package com.vellumhub.security.jwt;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.assertThat;

class HumanApiAccessTest {
    @Test
    void rolesNeverImplyScopesAndScopesNeverImplyRoles() {
        var converter = JwtAccessToken.servletConverter();
        var jwt = Jwt.withTokenValue("fixture").header("alg", "RS256")
                .subject("018d6373-864a-7000-8000-000000000001")
                .claim("grant_type", "authorization_code").claim("roles", List.of("ADMIN"));
        assertThat(HumanApiAccess.allowed(converter.convert(jwt.build()), "user:write", true)).isFalse();
        jwt.claim("scope", "user:write");
        assertThat(HumanApiAccess.allowed(converter.convert(jwt.build()), "user:write", true)).isTrue();
        assertThat(HumanApiAccess.allowed(converter.convert(jwt.build()), "catalog:write", true)).isFalse();
        jwt.claim("roles", List.of());
        assertThat(HumanApiAccess.allowed(converter.convert(jwt.build()), "user:write", false)).isFalse();
        jwt.claim("roles", List.of("ADMIN")).claim("grant_type", "client_credentials");
        assertThat(HumanApiAccess.allowed(converter.convert(jwt.build()), "user:write", true)).isFalse();
    }
}
