package com.mrs.engagement_service.module.rating.domain.port;

import com.mrs.engagement_service.module.rating.application.dto.filter.RatingFilter;
import com.mrs.engagement_service.module.rating.domain.model.BookProgress;
import com.mrs.engagement_service.module.rating.domain.model.EngagementStats;
import com.mrs.engagement_service.module.book_progress.domain.model.Rating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;
import java.util.UUID;

public interface RatingRepository {
    void save(Rating rating);

    Page<Rating> findAll(UUID userId, RatingFilter ratingFilter, PageRequest pageRequest);

    EngagementStats findStatusByMediaId(UUID mediaId);

    boolean existsByUserIdAndBookId(UUID uuid, UUID uuid1);
}
