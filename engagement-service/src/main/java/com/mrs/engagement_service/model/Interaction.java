package com.mrs.engagement_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Setter
@Getter
@AllArgsConstructor
public class Interaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID mediaId;

    @Enumerated(EnumType.STRING)
    private InteractionType type;

    private LocalDateTime timestamp;

    public Interaction() {
    }

    public Interaction(UUID userId, UUID mediaId, InteractionType type, LocalDateTime timestamp) {
        this.userId = userId;
        this.mediaId = mediaId;
        this.type = type;
        this.timestamp = timestamp;
    }
}
