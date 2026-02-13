package com.mrs.engagement_service.module.rating.domain.port;

import com.mrs.engagement_service.module.rating.domain.filter.RatingFilter;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

public interface RatingRepository {
    void save(Rating rating);

    Page<Rating> findAll(UUID userId, RatingFilter ratingFilter, PageRequest pageRequest);

    boolean existsByUserIdAndBookId(UUID uuid, UUID uuid1);
}
