package com.mrs.recommendation_service.infrastructure.repository.media_feature;

import com.mrs.recommendation_service.application.dto.MediaFeatureResponse;
import com.mrs.recommendation_service.domain.model.MediaFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaMediaFeatureRepository extends JpaRepository<MediaFeature, UUID> {

    @Query(value = """
    WITH current_user AS (
        SELECT profile_vector, interacted_media_ids 
        FROM user_profiles 
        WHERE user_id = :userId
    )
    SELECT m.media_id 
    FROM media_features m, current_user u
    WHERE m.media_id NOT IN (SELECT unnest(u.interacted_media_ids))
    ORDER BY (m.embedding <=> u.profile_vector) * 0.7 + (m.popularity_score * 0.3) ASC
    LIMIT :limit OFFSET :offset
    """, nativeQuery = true)
    List<UUID> findTopVectorRecommendations(UUID userId, int limit, int offset);

    @Query(value = """
            SELECT m.media_id
            FROM media_features m
            ORDER BY m.popularity_score
            LIMIT :limit OFFSET :offset
            """)
    List<UUID> findMostPopularMedias(int limit, int offset);

}
