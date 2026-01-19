package com.mrs.recommendation_service.application.event;

import java.util.UUID;

public record DeleteMediaEvent(
        UUID mediaId
) {
}
