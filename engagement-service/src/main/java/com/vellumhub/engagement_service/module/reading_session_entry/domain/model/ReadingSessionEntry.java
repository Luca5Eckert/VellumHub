package com.vellumhub.engagement_service.module.reading_session_entry.domain.model;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reading_session_entries")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReadingSessionEntry {

    @Id
    private Long id;

    private UUID userId;

    @ManyToOne(fetch = FetchType.EAGER)
    private BookSnapshot bookSnapshot;


    @Enumerated(EnumType.STRING)
    private ReadingSessionType readingSessionType;

    private Instant timestamp;


    public static ReadingSessionEntry create(BookSnapshot bookSnapshot, UUID userId, ReadingSessionType type) {
        return ReadingSessionEntry.builder()
                .readingSessionType(type)
                .userId(userId)
                .bookSnapshot(bookSnapshot)
                .build();

    }
}
