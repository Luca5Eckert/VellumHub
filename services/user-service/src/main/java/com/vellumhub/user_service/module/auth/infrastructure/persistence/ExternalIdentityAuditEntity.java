package com.vellumhub.user_service.module.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_identity_audit")
public class ExternalIdentityAuditEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String issuer;

    @Column(name = "subject_sha256", length = 64, nullable = false)
    private String subjectHash;

    @Column(nullable = false, length = 32)
    private String action;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected ExternalIdentityAuditEntity() {}

    public ExternalIdentityAuditEntity(UUID userId, String issuer, String subjectHash, String action) {
        this.userId = userId;
        this.issuer = issuer;
        this.subjectHash = subjectHash;
        this.action = action;
    }
}
