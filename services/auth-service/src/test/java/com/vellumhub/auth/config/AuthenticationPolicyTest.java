package com.vellumhub.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.vellumhub.auth.identity.IdentityRepository;
import com.vellumhub.auth.identity.IdentityRole;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

class AuthenticationPolicyTest {
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void disabledAccountLosesExistingBrowserSession() throws Exception {
        var identities = mock(IdentityRepository.class);
        UUID id = UUID.randomUUID();
        when(identities.findById(id)).thenReturn(Optional.of(new IdentityRepository.Identity(id, "local", "hash", IdentityRole.USER, false)));
        var request = new MockHttpServletRequest();
        var session = (MockHttpSession) request.getSession();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(id.toString(), null, List.of()));
        var chain = mock(FilterChain.class);
        new ActiveIdentitySessionFilter(identities).doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test void disabledAccountCannotRedeemCodeFromEarlierSession() {
        var identities = mock(IdentityRepository.class);
        var context = mock(JwtEncodingContext.class);
        UUID id = UUID.randomUUID();
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE);
        when(context.getPrincipal()).thenReturn(UsernamePasswordAuthenticationToken.authenticated(id.toString(), null, List.of()));
        when(identities.findById(id)).thenReturn(Optional.of(new IdentityRepository.Identity(id, "local", "hash", IdentityRole.USER, false)));
        assertThatThrownBy(() -> new AuthorizationServerConfig().accessTokenClaims(identities, "api://vellumhub").customize(context))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, error ->
                        assertThat(error.getError().getErrorCode()).isEqualTo("invalid_grant"));
        verify(identities).findById(id);
    }

    @Test void sharedAttemptLimitRejectsOidcStartsAndCountsWithoutIdentityLabels() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(21);
        var metrics = new SimpleMeterRegistry();
        var filter = new AuthenticationThrottleFilter(jdbc, metrics, 20,
                Clock.fixed(Instant.parse("2026-10-26T12:00:01Z"), ZoneOffset.UTC));
        var request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        request.setServletPath("/oauth2/authorization/google");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("59");
        assertThat(metrics.get("vellum.auth.attempts").tag("outcome", "throttled").counter().count()).isEqualTo(1);
        verifyNoInteractions(chain);
    }
}
