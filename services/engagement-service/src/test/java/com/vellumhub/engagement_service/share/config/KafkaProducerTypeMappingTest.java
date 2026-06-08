package com.vellumhub.engagement_service.share.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaProducerTypeMappingTest {

    @Test
    @DisplayName("Should publish cross-service engagement events with stable type aliases")
    void shouldPublishCrossServiceEngagementEventsWithStableTypeAliases() throws IOException {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(Path.of("src", "main", "resources", "application.properties"))) {
            properties.load(input);
        }

        String typeMapping = properties.getProperty("spring.kafka.producer.properties.spring.json.type.mapping");

        assertThat(typeMapping)
                .contains("create_rating_event:com.vellumhub.engagement_service.module.rating.domain.event.CreatedRatingEvent")
                .contains("reaction_changed_event:com.vellumhub.engagement_service.module.reaction.domain.event.ReactionChangedEvent");
    }
}
