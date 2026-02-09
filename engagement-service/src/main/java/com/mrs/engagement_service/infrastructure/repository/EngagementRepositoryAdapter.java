package com.mrs.engagement_service.infrastructure.repository;

import com.mrs.engagement_service.application.dto.filter.RatingFilter;
import com.mrs.engagement_service.domain.model.EngagementStats;
import com.mrs.engagement_service.infrastructure.provider.RatingFilterProvider;
import com.mrs.engagement_service.domain.model.Rating;
import com.mrs.engagement_service.domain.port.EngagementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EngagementRepositoryAdapter implements EngagementRepository {

    public final EngagementRepositoryJpa engagementRepositoryJpa;
    public final RatingFilterProvider ratingFilterProvider;

    public EngagementRepositoryAdapter(EngagementRepositoryJpa engagementRepositoryJpa, RatingFilterProvider ratingFilterProvider) {
        this.engagementRepositoryJpa = engagementRepositoryJpa;
        this.ratingFilterProvider = ratingFilterProvider;
    }

    @Override
    public void save(Rating rating) {
        engagementRepositoryJpa.save(rating);
    }

    @Override
    public Page<Rating> findAll(UUID userId, RatingFilter ratingFilter, PageRequest pageRequest) {
        Specification<Rating> ratingSpecification = ratingFilterProvider.of(
                ratingFilter,
                userId
        );

        return engagementRepositoryJpa.findAll(ratingSpecification, pageRequest);
    }

    @Override
    public EngagementStats findStatusByMediaId(UUID mediaId) {
        return engagementRepositoryJpa.findStatusByMediaId(mediaId);
    }

}
