package com.vellumhub.user_service.module.user_preference.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.preference-outbox.enabled", havingValue = "true", matchIfMissing = true)
class PreferenceOutboxScheduler {
    private final KafkaPreferenceOutbox outbox;

    PreferenceOutboxScheduler(KafkaPreferenceOutbox outbox) {
        this.outbox = outbox;
    }

    @Scheduled(fixedDelayString = "${app.preference-outbox.poll-ms:500}")
    void dispatch() {
        outbox.dispatchNext();
    }
}
