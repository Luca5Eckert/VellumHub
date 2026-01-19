package com.mrs.catalog_service.domain.event;

import java.util.UUID;

public record DeleteMediaEvent(
        UUID mediaId
) {
}
