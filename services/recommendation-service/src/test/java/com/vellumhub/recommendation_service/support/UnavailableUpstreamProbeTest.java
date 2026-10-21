package com.vellumhub.recommendation_service.support;

import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/** Negative control proves the canary detects real network traffic, not just HTTP responses. */
class UnavailableUpstreamProbeTest {
    @Test
    void unexpectedSynchronousConnectionIsRecordedEvenWhenUpstreamReturns503() throws Exception {
        long before = UnavailableUpstreamProbe.attempts();
        HttpURLConnection connection = (HttpURLConnection) URI.create(UnavailableUpstreamProbe.endpoint())
                .toURL().openConnection();
        connection.setConnectTimeout(1_000);
        connection.setReadTimeout(1_000);
        try {
            assertThat(connection.getResponseCode()).isEqualTo(503);
        } finally {
            connection.disconnect();
        }
        assertThat(UnavailableUpstreamProbe.attempts() - before).isEqualTo(1);
    }
}
