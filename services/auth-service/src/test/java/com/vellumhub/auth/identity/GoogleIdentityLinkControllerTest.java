package com.vellumhub.auth.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class GoogleIdentityLinkControllerTest {
    private final IdentityRepository identities = mock(IdentityRepository.class);
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final UUID id = UUID.fromString("019a0000-1111-7000-8000-000000000001");
    private final GoogleIdentityLinkController controller =
            new GoogleIdentityLinkController(identities, decoder, passwords, "google-client");

    private UsernamePasswordAuthenticationToken authentication() {
        return new UsernamePasswordAuthenticationToken(id.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private MockHttpServletRequest challengedRequest() {
        when(identities.findById(id)).thenReturn(java.util.Optional.of(
                new IdentityRepository.Identity(id, "ada@example.org", "{bcrypt}hash", true)));
        when(passwords.matches("correct-password", "{bcrypt}hash")).thenReturn(true);
        var request = new MockHttpServletRequest();
        controller.challenge(new GoogleIdentityLinkController.PasswordProof("correct-password"),
                authentication(), request);
        return request;
    }

    @Test
    void linksOnlyWithValidGoogleTokenAndMatchingOneTimeNonce() {
        var request = challengedRequest();
        var challenge = (GoogleIdentityLinkController.LinkChallenge)
                request.getSession().getAttribute(GoogleIdentityLinkController.CHALLENGE_KEY);
        Jwt jwt = Jwt.withTokenValue("signed-google-token")
                .header("alg", "RS256")
                .claim("iss", "https://accounts.google.com")
                .claim("sub", "google-subject-1")
                .claim("aud", List.of("google-client"))
                .claim("email_verified", true)
                .claim("nonce", challenge.nonce())
                .build();
        when(decoder.decode("signed-google-token")).thenReturn(jwt);
        assertThat(controller.link(new GoogleIdentityLinkController.GoogleIdTokenProof("signed-google-token"),
                authentication(), request).getStatusCode().value()).isEqualTo(204);
        verify(identities).linkVerifiedGoogle(id, "https://accounts.google.com", "google-subject-1");
        assertThat(request.getSession().getAttribute(GoogleIdentityLinkController.CHALLENGE_KEY)).isNull();
        assertThatThrownBy(() -> controller.link(
                new GoogleIdentityLinkController.GoogleIdTokenProof("signed-google-token"),
                authentication(), request))
                .isInstanceOf(GoogleIdentityLinkController.LinkForbiddenException.class);
    }

    @Test
    void rejectsWrongNonceWithoutWritingBinding() {
        var request = challengedRequest();
        Jwt jwt = Jwt.withTokenValue("bad-google-token")
                .header("alg", "RS256")
                .claim("iss", "https://accounts.google.com")
                .claim("sub", "google-subject-1")
                .claim("aud", List.of("google-client"))
                .claim("nonce", "different-nonce")
                .claim("email_verified", true)
                .build();
        when(decoder.decode("bad-google-token")).thenReturn(jwt);
        assertThatThrownBy(() -> controller.link(
                new GoogleIdentityLinkController.GoogleIdTokenProof("bad-google-token"),
                authentication(), request))
                .isInstanceOf(GoogleIdentityLinkController.LinkForbiddenException.class);
        verify(identities, never()).linkVerifiedGoogle(any(), anyString(), anyString());
    }

    @Test
    void incorrectPasswordCannotMintChallenge() {
        when(identities.findById(id)).thenReturn(java.util.Optional.of(
                new IdentityRepository.Identity(id, "ada@example.org", "{bcrypt}hash", true)));
        var request = new MockHttpServletRequest();
        assertThatThrownBy(() -> controller.challenge(
                new GoogleIdentityLinkController.PasswordProof("wrong"), authentication(), request))
                .isInstanceOf(GoogleIdentityLinkController.LinkForbiddenException.class);
        assertThat(request.getSession(false)).isNull();
    }
}
