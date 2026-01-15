package com.mrs.engagement_service.repository;

import com.mrs.engagement_service.dto.filter.InteractionFilter;
import com.mrs.engagement_service.model.Interaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

public interface EngagementRepository {
    void save(Interaction interaction);

    Page<Interaction> findAll(UUID userId, InteractionFilter interactionFilter, PageRequest pageRequest);
}
