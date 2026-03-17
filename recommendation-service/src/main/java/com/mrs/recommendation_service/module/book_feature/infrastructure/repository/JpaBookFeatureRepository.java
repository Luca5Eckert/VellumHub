package com.mrs.recommendation_service.module.book_feature.infrastructure.repository;

import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaBookFeatureRepository extends JpaRepository<BookFeature, UUID> {

    @Query(value = """
                WITH user_data AS (
                    -- 1. Isolamos os dados do usuário e garantimos um array vazio seguro para a filtragem
                    SELECT
                        profile_vector,
                        COALESCE(interacted_book_ids, '{}'::uuid[]) AS interacted_ids
                    FROM user_profiles
                    WHERE user_id = :userId
                ),
                candidates AS (
                    -- 2. Busca Rápida (Candidate Generation) usando o Índice HNSW
                    SELECT
                        b.book_id,
                        b.popularity_score,
                        (b.embedding <=> u.profile_vector) AS vector_dist
                    FROM book_features b
                    CROSS JOIN user_data u
                    WHERE b.book_id <> ALL(u.interacted_ids)
                    ORDER BY b.embedding <=> u.profile_vector ASC
                    LIMIT 200
                )
                -- 3. Re-rankeamento (Scoring) e Paginação
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
            ORDER BY b.popularity_score DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<UUID> findMostPopularMedias(
            @Param("limit") int limit,
            @Param("offset") int offset
    );
}