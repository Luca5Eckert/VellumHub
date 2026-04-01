package com.vellumhub.engagement_service.module.interaction.domain.model;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.interaction.domain.exception.InteractionException;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "interactions")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Interaction {

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
    private TypeInteraction typeInteraction;

    public static Interaction of(UUID userId, BookSnapshot snapshot, TypeInteraction type) {
        return Interaction.builder()
                .userId(userId)
                .bookSnapshot(snapshot)
                .typeInteraction(type)
                .build();
    }

    public void updateType(TypeInteraction typeInteraction, UUID userId) {
        if(typeInteraction == null) throw new InteractionException("Type interaction cannot be null");
        if(!userId.equals(this.userId)) throw new InteractionException("User cannot update interaction of another user");

        this.typeInteraction = typeInteraction;
    }

}