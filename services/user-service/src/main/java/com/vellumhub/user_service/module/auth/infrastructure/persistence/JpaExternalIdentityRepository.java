package com.vellumhub.user_service.module.auth.infrastructure.persistence;

import com.vellumhub.user_service.module.auth.domain.model.ExternalIdentity;
import com.vellumhub.user_service.module.auth.domain.port.ExternalIdentityRepository;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaExternalIdentityRepository implements ExternalIdentityRepository {
    private final ExternalIdentityJpaRepository identities;
    private final ExternalIdentityAuditJpaRepository audit;

    public JpaExternalIdentityRepository(ExternalIdentityJpaRepository identities,
                                         ExternalIdentityAuditJpaRepository audit) {
        this.identities = identities;
        this.audit = audit;
    }

    @Override
    public Optional<ExternalIdentity> findByIdentity(String issuer, String subject) {
        return identities.findByIssuerAndSubject(issuer, subject).map(this::toDomain);
    }

    @Override
    public Optional<ExternalIdentity> findByUserAndIssuer(UUID userId, String issuer) {
        return identities.findByUserIdAndIssuer(userId, issuer).map(this::toDomain);
    }

    @Override
    public void link(ExternalIdentity identity, String action) {
        identities.saveAndFlush(new ExternalIdentityEntity(
                identity.issuer(), identity.subject(), identity.userId()));
        audit.save(new ExternalIdentityAuditEntity(identity.userId(), identity.issuer(),
                sha256(identity.subject()), action));
    }

    @Override
    public void unlink(ExternalIdentity identity) {
        identities.deleteByUserIdAndIssuer(identity.userId(), identity.issuer());
        audit.save(new ExternalIdentityAuditEntity(identity.userId(), identity.issuer(),
                sha256(identity.subject()), "UNLINKED"));
    }

    private ExternalIdentity toDomain(ExternalIdentityEntity entity) {
        return new ExternalIdentity(entity.getIssuer(), entity.getSubject(), entity.getUserId());
    }

    private static String sha256(String subject) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(subject.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest unavailable", ex);
        }
    }
}
