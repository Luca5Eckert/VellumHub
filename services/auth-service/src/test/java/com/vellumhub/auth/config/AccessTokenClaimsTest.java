package com.vellumhub.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

class AccessTokenClaimsTest {
    private final com.vellumhub.auth.identity.IdentityRepository identities = mock(com.vellumhub.auth.identity.IdentityRepository.class);
    private final AuthorizationServerConfig config = new AuthorizationServerConfig();

    @Test
    void emitsRfc9068AccessTokenWithCanonicalSubjectNotEmail() {
        var subject = UUID.randomUUID().toString();
        var context = mock(JwtEncodingContext.class);
        var client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("vellumhub-spa-poc")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://127.0.0.1:9100/index.html")
                .scope("openid")
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256);
        var claims = JwtClaimsSet.builder();
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getJwsHeader()).thenReturn(header);
        when(context.getClaims()).thenReturn(claims);
        when(context.getPrincipal()).thenReturn(new UsernamePasswordAuthenticationToken(subject, "irrelevant"));
        when(context.getRegisteredClient()).thenReturn(client);

        when(identities.findById(UUID.fromString(subject))).thenReturn(java.util.Optional.of(
                new com.vellumhub.auth.identity.IdentityRepository.Identity(UUID.fromString(subject), "local", "hash", com.vellumhub.auth.identity.IdentityRole.ADMIN, true)));
        config.accessTokenClaims(identities, "api://vellumhub").customize(context);

        assertThat(header.build().getType()).isEqualTo("at+jwt");
        assertThat(claims.build().getSubject()).isEqualTo(subject);
        assertThat(claims.build().getAudience()).containsExactly("api://vellumhub");
        assertThat(claims.build().getClaimAsString("client_id")).isEqualTo("vellumhub-spa-poc");
        assertThat(claims.build().getId()).isNotBlank();
        assertThat(claims.build().getClaimAsStringList("roles")).containsExactly("ADMIN");
    }

    @Test
    void doesNotConvertOidcIdTokenIntoApiAccessToken() {
        var context = mock(JwtEncodingContext.class);
        var header = JwsHeader.with(SignatureAlgorithm.RS256);
        var claims = JwtClaimsSet.builder().subject("oidc-subject");
        when(context.getTokenType()).thenReturn(new OAuth2TokenType("id_token"));
        when(context.getJwsHeader()).thenReturn(header);
        when(context.getClaims()).thenReturn(claims);
        config.accessTokenClaims(identities, "api://vellumhub").customize(context);
        assertThat(header.build().getType()).isNotEqualTo("at+jwt");
        assertThat(claims.build().getAudience()).isNullOrEmpty();
    }
}
