package com.vellumhub.engagement_service.module.reaction.infrastructure.persistence.repository;

import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import java.util.List;
import java.util.UUID;

public interface JpaReactionRepository extends JpaRepository<Reaction, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reaction from Reaction reaction where reaction.id = :id")
    Optional<Reaction> findByIdForUpdate(@Param("id") Long id);

    List<Reaction> findAllByUserId(UUID userId);
}
