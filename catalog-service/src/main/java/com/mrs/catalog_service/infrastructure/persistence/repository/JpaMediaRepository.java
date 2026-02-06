package com.mrs.catalog_service.infrastructure.persistence.repository;

import com.mrs.catalog_service.domain.model.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JpaMediaRepository extends JpaRepository<Media, UUID> {
}
