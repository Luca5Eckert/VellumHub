package com.vellumhub.engagement_service.module.reading_session_entry.domain.port;

import com.vellumhub.engagement_service.module.reading_session_entry.domain.event.CreateReadingSessionEvent;

public interface ReadingSessionEventPublisher {

    void publish(CreateReadingSessionEvent event);


}
