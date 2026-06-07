package com.vellumhub.engagement_service.share.config;

import com.vellumhub.engagement_service.share.metrics.VellumHubMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class KafkaRetryConfigTest {

    @Test
    void dltLoggingDoesNotExposeRawPayload(CapturedOutput output) {
        KafkaRetryConfig config = new KafkaRetryConfig(new VellumHubMetrics(new SimpleMeterRegistry()));

        config.consumeDlt(
                "{\"password\":\"secret\",\"token\":\"full.jwt.value\"}".getBytes(StandardCharsets.UTF_8),
                "created-book-dlt",
                "created-book",
                "boom"
        );

        assertThat(output)
                .contains("Kafka DLT message quarantined")
                .contains("originalTopic=created-book")
                .contains("dltTopic=created-book-dlt")
                .contains("payloadBytes=");
        assertThat(output)
                .doesNotContain("secret")
                .doesNotContain("full.jwt.value")
                .doesNotContain("Message Payload");
    }

    @Test
    void dltConsumerCountsQuarantinedEvents() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        KafkaRetryConfig config = new KafkaRetryConfig(new VellumHubMetrics(meterRegistry));

        config.consumeDlt("{}".getBytes(StandardCharsets.UTF_8), "created-book-dlt", "created-book", "boom");

        assertThat(meterRegistry.get("vellumhub.kafka.dlt.events")
                .tag("topic", "created-book")
                .tag("consumer_group", "engagement-service-dlt-group")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void dltConsumerFallsBackToDltTopicWhenOriginalTopicHeaderIsMissing() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        KafkaRetryConfig config = new KafkaRetryConfig(new VellumHubMetrics(meterRegistry));

        config.consumeDlt("{}".getBytes(StandardCharsets.UTF_8), "deleted-book-dlt", null, null);

        assertThat(meterRegistry.get("vellumhub.kafka.dlt.events")
                .tag("topic", "deleted-book")
                .tag("consumer_group", "engagement-service-dlt-group")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
