package com.vellumhub.user_service.module.auth.domain.port;

import com.vellumhub.user_service.module.auth.domain.model.ExternalIdentity;
import java.util.Optional;
import java.util.UUID;

public interface ExternalIdentityRepository {
    Optional<ExternalIdentity> findByIdentity(String issuer, String subject);
    Optional<ExternalIdentity> findByUserAndIssuer(UUID userId, String issuer);
    void link(ExternalIdentity identity, String action);
    void unlink(ExternalIdentity identity);
}
