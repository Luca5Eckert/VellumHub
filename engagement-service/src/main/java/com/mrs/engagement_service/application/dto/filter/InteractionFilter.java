package com.mrs.engagement_service.application.dto.filter;

import com.mrs.engagement_service.domain.model.InteractionType;

import java.time.OffsetDateTime;

/**
 * Filtro imutável para busca de interações.
 */
public record InteractionFilter(
        InteractionType type,
        OffsetDateTime from,
        OffsetDateTime to
) {
    public InteractionFilter {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Intervalo de datas inválido: 'from' deve ser anterior a 'to'.");
        }
    }

    public boolean hasType() {
        return type != null;
    }
}