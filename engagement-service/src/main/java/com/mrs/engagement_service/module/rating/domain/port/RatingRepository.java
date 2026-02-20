package com.mrs.engagement_service.module.rating.domain.port;

import com.mrs.engagement_service.module.rating.domain.filter.RatingFilter;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;
import java.util.UUID;

public interface RatingRepository {
    Rating save(Rating rating);

    Page<Rating> findAll(UUID userId, RatingFilter ratingFilter, PageRequest pageRequest);

    boolean existsByUserIdAndBookId(UUID uuid, UUID uuid1);

    Optional<Rating> findById(long id);

    boolean existsbyId(Long ratingId);

    void deleteById(Long ratingId);
}
