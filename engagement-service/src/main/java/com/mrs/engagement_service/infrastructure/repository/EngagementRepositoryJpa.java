package com.mrs.engagement_service.infrastructure.repository;

import com.mrs.engagement_service.domain.model.EngagementStatus;
import com.mrs.engagement_service.domain.model.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EngagementRepositoryJpa extends JpaRepository<Interaction, Long>, JpaSpecificationExecutor<Interaction> {

    @Query("""
        SELECT 
            COUNT(CASE WHEN i.type = 'VIEW' THEN 1 END) as totalViews,
            COUNT(CASE WHEN i.type = 'LIKE' THEN 1 END) as totalLikes,
            COUNT(CASE WHEN i.type = 'DISLIKE' THEN 1 END) as totalDislikes,
            AVG(CASE WHEN i.type = 'RATING' THEN i.interactionValue END) as averageRating,
            COUNT(CASE WHEN i.type = 'RATING' THEN 1 END) as totalRatings,
            COUNT(i) as totalInteractions,
            (COUNT(CASE WHEN i.type = 'LIKE' THEN 1 END) * 1.0 / NULLIF(COUNT(i), 0)) as popularityScore
        FROM Interaction i
        WHERE i.mediaId = :mediaId
    """)
    EngagementStatus findStatusByMediaId(UUID mediaId);

}
