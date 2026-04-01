package com.vellumhub.engagement_service.module.reaction.domain.model;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.reaction.domain.exception.ReactionException;
import jakarta.persistence.*;
import lombok.*;

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

    public static Reaction of(UUID userId, BookSnapshot snapshot, TypeReaction type) {
        return Reaction.builder()
                .userId(userId)
                .bookSnapshot(snapshot)
                .typeReaction(type)
                .build();
    }

    public void updateType(TypeReaction typeReaction, UUID userId) {
        if(typeReaction == null) throw new ReactionException("Type reaction cannot be null");
        if(!userId.equals(this.userId)) throw new ReactionException("User cannot update reaction of another user");

        this.typeReaction = typeReaction;
    }

}