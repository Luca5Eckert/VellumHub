package com.mrs.engagement_service.domain.port;

import com.mrs.engagement_service.application.dto.filter.InteractionFilter;
import com.mrs.engagement_service.domain.model.Interaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

public interface EngagementRepository {
    void save(Interaction interaction);

    Page<Interaction> findAll(UUID userId, InteractionFilter interactionFilter, PageRequest pageRequest);
}
