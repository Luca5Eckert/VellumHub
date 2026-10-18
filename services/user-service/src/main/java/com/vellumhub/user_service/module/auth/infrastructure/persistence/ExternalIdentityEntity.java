package com.vellumhub.user_service.module.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_identities", uniqueConstraints = {
        @UniqueConstraint(name = "uk_external_identity_provider_subject", columnNames = {"issuer", "subject"}),
        @UniqueConstraint(name = "uk_external_identity_user_provider", columnNames = {"user_id", "issuer"})
})
public class ExternalIdentityEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 255)
    private String issuer;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExternalIdentityEntity() {}

    public ExternalIdentityEntity(String issuer, String subject, UUID userId) {
        this.issuer = issuer;
        this.subject = subject;
        this.userId = userId;
    }
    public String getIssuer() { return issuer; }
    public String getSubject() { return subject; }
    public UUID getUserId() { return userId; }
}
