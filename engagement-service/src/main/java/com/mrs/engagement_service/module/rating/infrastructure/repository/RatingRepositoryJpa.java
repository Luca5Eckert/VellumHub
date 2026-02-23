package com.mrs.engagement_service.module.rating.infrastructure.repository;

import com.mrs.engagement_service.module.rating.domain.model.Rating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RatingRepositoryJpa extends JpaRepository<Rating, Long>, JpaSpecificationExecutor<Rating> {

    boolean existsByUserIdAndBookId(UUID userId, UUID bookId);

    Page<Rating> findAllByBookId(int bookId, PageRequest pageRequest);
}
