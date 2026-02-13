package com.mrs.engagement_service.module.rating.infrastructure.repository;

import com.mrs.engagement_service.module.book_progress.domain.model.EngagementStats;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EngagementRepositoryJpa extends JpaRepository<Rating, Long>, JpaSpecificationExecutor<Rating> {

    @Query("""
        SELECT 
            AVG(r.stars) as averageRating,
            COUNT(r) as totalRatings
        FROM Rating r
        WHERE r.mediaId = :mediaId
    """)
    EngagementStats findStatusByMediaId(UUID mediaId);

    boolean existsByUserIdAndBookId(UUID userId, UUID bookId);
}
