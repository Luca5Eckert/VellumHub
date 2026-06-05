package com.vellumhub.recommendation_service.share.kafka.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class KafkaRetryConfigTest {

    @Test
    void dltLoggingDoesNotExposeRawPayload(CapturedOutput output) {
        KafkaRetryConfig config = new KafkaRetryConfig();

        config.consumeDlt(
                "{\"password\":\"secret\",\"token\":\"full.jwt.value\"}",
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
}
