package com.mrs.catalog_service.infrastructure.persistence.repository;

import com.mrs.catalog_service.domain.model.Media;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaMediaRepository extends JpaRepository<Media, UUID> {
}
