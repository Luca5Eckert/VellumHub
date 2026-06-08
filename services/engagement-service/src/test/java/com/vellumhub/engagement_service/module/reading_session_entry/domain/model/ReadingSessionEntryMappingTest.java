package com.vellumhub.engagement_service.module.reading_session_entry.domain.model;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingSessionEntryMappingTest {

    @Test
    void idShouldBeGeneratedByTheDatabase() throws NoSuchFieldException {
        var idField = ReadingSessionEntry.class.getDeclaredField("id");

        var generatedValue = idField.getAnnotation(GeneratedValue.class);

        assertThat(generatedValue).isNotNull();
        assertThat(generatedValue.strategy()).isEqualTo(GenerationType.IDENTITY);
    }
}
