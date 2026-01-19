package com.mrs.catalog_service.domain.event;

import java.util.List;
import java.util.UUID;

public record CreateMediaEvent(
        UUID mediaId,
        List<String> genres
) {
}
