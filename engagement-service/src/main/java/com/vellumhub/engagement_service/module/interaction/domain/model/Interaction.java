package com.vellumhub.engagement_service.module.interaction.domain.model;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
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
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "interaction_gen")
    @SequenceGenerator(name = "interaction_gen", sequenceName = "interactions_seq", allocationSize = 1)
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

}