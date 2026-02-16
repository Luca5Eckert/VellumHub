package com.mrs.recommendation_service.module.book_feature.application.event;

import com.mrs.recommendation_service.module.book_feature.domain.model.Genre;

import java.util.List;
import java.util.UUID;

public record UpdateBookEvent(
        UUID bookId,
        List<Genre> genres
) {
}
