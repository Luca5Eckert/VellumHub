package com.mrs.recommendation_service.module.book_feature.infrastructure.repository;

import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;
import feign.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaBookFeatureRepository extends JpaRepository<BookFeature, UUID> {

    @Query(value = """
    WITH current_user AS (
        SELECT profile_vector, interacted_book_ids 
        FROM user_profiles 
        WHERE user_id = :userId
    ),
    candidates AS (
        SELECT 
            b.book_id, 
            b.popularity_score,
            (b.embedding <=> u.profile_vector) as vector_dist
        FROM book_features b
        CROSS JOIN current_user u
        WHERE NOT (b.book_id = ANY(COALESCE(u.interacted_book_ids, ARRAY[]::uuid[])))
        ORDER BY b.embedding <=> u.profile_vector ASC
        LIMIT 200 
    )
    SELECT c.book_id 
    FROM candidates c
    ORDER BY 
        (c.vector_dist * 0.7) + ((1 - COALESCE(c.popularity_score, 0)) * 0.3) ASC
    LIMIT :limit OFFSET :offset
    """, nativeQuery = true)
    List<UUID> findTopVectorRecommendations(
            @Param("userId") UUID userId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query(value = """
            SELECT b.book_id
            FROM book_features b
            ORDER BY b.popularity_score
            LIMIT :limit OFFSET :offset
            """)
    List<UUID> findMostPopularMedias(int limit, int offset);

}
