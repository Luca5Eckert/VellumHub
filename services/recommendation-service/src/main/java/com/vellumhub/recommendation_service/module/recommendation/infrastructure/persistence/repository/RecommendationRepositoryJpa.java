package com.vellumhub.recommendation_service.module.recommendation.infrastructure.persistence.repository;

import com.vellumhub.recommendation_service.module.recommendation.domain.model.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RecommendationRepositoryJpa extends JpaRepository<Recommendation, UUID> {

    /**
     * Hydrates the selected recommendation page and its genres in ONE SQL.
     * A normal findAllById followed by DTO mapping generated one lazy
     * @ElementCollection SELECT per book (N+1).
     *
     * The ranking/pagination already happened in pgvector; do not apply
     * pagination to this collection fetch join. The use case restores rank order.
     */
    @Query("select distinct r from Recommendation r left join fetch r.genres where r.bookId in :ids")
    List<Recommendation> findPageWithGenres(@Param("ids") List<UUID> ids);
}
