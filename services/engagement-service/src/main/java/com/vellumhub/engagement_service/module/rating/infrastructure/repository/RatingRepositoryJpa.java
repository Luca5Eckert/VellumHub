package com.vellumhub.engagement_service.module.rating.infrastructure.repository;

import com.vellumhub.engagement_service.module.rating.domain.model.Rating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.UUID;

@Repository
public interface RatingRepositoryJpa extends JpaRepository<Rating, Long>, JpaSpecificationExecutor<Rating> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Rating r where r.id=:id")
    Optional<Rating> findByIdForUpdate(@Param("id") Long id);

    boolean existsByUserIdAndBookId(UUID userId, UUID bookId);

    Page<Rating> findAllByBookId(UUID bookId, PageRequest pageRequest);
}

