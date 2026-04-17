package com.vellumhub.engagement_service.module.book_progress_event.domain.model;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "book_progress_events")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BookProgressEvent {

    @Id
    private Long id;

    private UUID userId;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private BookSnapshot bookSnapshot;


    @Enumerated(EnumType.STRING)
    private ProgressType progressType;

    private Instant timestamp;


}
