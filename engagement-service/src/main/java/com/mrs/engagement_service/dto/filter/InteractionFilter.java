package com.mrs.engagement_service.dto.filter;

import com.mrs.engagement_service.model.InteractionType;
import lombok.Builder;

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