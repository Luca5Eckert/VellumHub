package com.vellumhub.auth.config;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AbsoluteSessionLifetimeFilterTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-25T20:00:00Z"), ZoneOffset.UTC);

    @Test
    void invalidatesSessionAfterAbsoluteLifetime() throws Exception {
        var filter = new AbsoluteSessionLifetimeFilter(Duration.ofHours(12), clock);
        var request = spy(new MockHttpServletRequest());
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getCreationTime()).thenReturn(clock.millis() - Duration.ofHours(13).toMillis());
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        verify(session).invalidate();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void keepsUnexpiredSession() throws Exception {
        var filter = new AbsoluteSessionLifetimeFilter(Duration.ofHours(12), clock);
        var request = spy(new MockHttpServletRequest());
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getCreationTime()).thenReturn(clock.millis() - Duration.ofHours(2).toMillis());
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        verify(session, never()).invalidate();
    }
}
