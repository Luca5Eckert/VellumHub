package com.vellumhub.user_service.module.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ExternalIdentityJpaRepository extends JpaRepository<ExternalIdentityEntity, UUID> {
    Optional<ExternalIdentityEntity> findByIssuerAndSubject(String issuer, String subject);
    Optional<ExternalIdentityEntity> findByUserIdAndIssuer(UUID userId, String issuer);
    long deleteByUserIdAndIssuer(UUID userId, String issuer);
}
