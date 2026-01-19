package com.mrs.engagement_service.infrastructure.repository;

import com.mrs.engagement_service.application.dto.filter.InteractionFilter;
import com.mrs.engagement_service.domain.model.EngagementStats;
import com.mrs.engagement_service.infrastructure.provider.InteractionFilterProvider;
import com.mrs.engagement_service.domain.model.Interaction;
import com.mrs.engagement_service.domain.port.EngagementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EngagementRepositoryAdapter implements EngagementRepository {

    public final EngagementRepositoryJpa engagementRepositoryJpa;
    public final InteractionFilterProvider interactionFilterProvider;

    public EngagementRepositoryAdapter(EngagementRepositoryJpa engagementRepositoryJpa, InteractionFilterProvider interactionFilterProvider) {
        this.engagementRepositoryJpa = engagementRepositoryJpa;
        this.interactionFilterProvider = interactionFilterProvider;
    }

    @Override
    public void save(Interaction interaction) {
        engagementRepositoryJpa.save(interaction);
    }

    @Override
    public Page<Interaction> findAll(UUID userId, InteractionFilter interactionFilter, PageRequest pageRequest) {
        Specification<Interaction> interactionSpecification = interactionFilterProvider.of(
                interactionFilter,
                userId
        );

        return engagementRepositoryJpa.findAll(interactionSpecification, pageRequest);
    }

    @Override
    public EngagementStats findStatusByMediaId(UUID mediaId) {
        return engagementRepositoryJpa.findStatusByMediaId(mediaId);
    }

}
