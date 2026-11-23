package com.vellumhub.user_service.module.user_preference.application.use_case;

import java.util.List;
import java.util.UUID;

public interface PreferenceOutbox {
    void enqueue(UUID userId, long revision, Long avroRevision, List<String> genres, String about);
}
