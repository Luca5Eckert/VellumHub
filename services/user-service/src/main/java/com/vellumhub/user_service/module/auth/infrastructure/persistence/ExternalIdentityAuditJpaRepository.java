package com.vellumhub.user_service.module.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ExternalIdentityAuditJpaRepository extends JpaRepository<ExternalIdentityAuditEntity, UUID> {
}
