package com.vellumhub.engagement_service.module.reaction.domain.model;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.reaction.domain.exception.ReactionException;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "reactions")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Reaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_snapshot_id")
    private BookSnapshot bookSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeReaction typeReaction;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Reaction of(UUID userId, BookSnapshot snapshot, TypeReaction type) {
        return of(userId, snapshot, type, Instant.now());
    }

    public static Reaction of(UUID userId, BookSnapshot snapshot, TypeReaction type, Instant occurredAt) {
        if (type == null) {
            throw new ReactionException("Type reaction cannot be null");
        }
        if (occurredAt == null) {
            throw new ReactionException("Reaction occurrence time cannot be null");
        }

        Instant persistedOccurrence = occurredAt.truncatedTo(ChronoUnit.MICROS);
        return Reaction.builder()
                .userId(userId)
                .bookSnapshot(snapshot)
                .typeReaction(type)
                .createdAt(persistedOccurrence)
                .updatedAt(persistedOccurrence)
                .build();
    }

    public TypeReaction updateType(TypeReaction typeReaction, UUID userId) {
        return updateType(typeReaction, userId, Instant.now());
    }

    public TypeReaction updateType(TypeReaction typeReaction, UUID userId, Instant occurredAt) {
        if (typeReaction == null) {
            throw new ReactionException("Type reaction cannot be null");
        }
        if (!userId.equals(this.userId)) {
            throw new ReactionException("User cannot update reaction of another user");
        }
        if (occurredAt == null) {
            throw new ReactionException("Reaction occurrence time cannot be null");
        }

        TypeReaction oldTypeReaction = this.typeReaction;
        this.typeReaction = typeReaction;
        this.updatedAt = occurredAt.truncatedTo(ChronoUnit.MICROS);

        return oldTypeReaction;
    }
}
