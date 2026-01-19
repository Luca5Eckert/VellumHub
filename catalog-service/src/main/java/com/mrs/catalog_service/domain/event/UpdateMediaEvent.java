package com.mrs.catalog_service.domain.event;

import java.util.List;

public record UpdateMediaEvent(
        String mediaId,
        List<String> genres
) {
}
