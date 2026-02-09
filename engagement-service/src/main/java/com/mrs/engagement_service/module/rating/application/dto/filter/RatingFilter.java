package com.mrs.engagement_service.module.rating.application.dto.filter;

import java.time.OffsetDateTime;

/**
 * Immutable filter for rating searches.
 */
public record RatingFilter(
        Integer minStars,
        Integer maxStars,
        OffsetDateTime from,
        OffsetDateTime to
) {
    public RatingFilter {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Intervalo de datas inválido: 'from' deve ser anterior a 'to'.");
        }
        if (minStars != null && (minStars < 0 || minStars > 5)) {
            throw new IllegalArgumentException("minStars deve estar entre 0 e 5.");
        }
        if (maxStars != null && (maxStars < 0 || maxStars > 5)) {
            throw new IllegalArgumentException("maxStars deve estar entre 0 e 5.");
        }
        if (minStars != null && maxStars != null && minStars > maxStars) {
            throw new IllegalArgumentException("minStars deve ser menor ou igual a maxStars.");
        }
    }

    public boolean hasMinStars() {
        return minStars != null;
    }

    public boolean hasMaxStars() {
        return maxStars != null;
    }
}