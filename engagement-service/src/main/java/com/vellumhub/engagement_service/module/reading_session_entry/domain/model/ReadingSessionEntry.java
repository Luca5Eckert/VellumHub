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

    private UUID readingSessionId;

    @Column(
            nullable = false,
            name = "user_id"
    )
    private UUID userId;

    @Column(
            nullable = false,
            name = "page_read"
    )
    private int pageRead;

    @ManyToOne(fetch = FetchType.EAGER)
    private BookSnapshot bookSnapshot;

    private String type;

    private Instant timestamp;

    public static ReadingSessionEntry create(UUID bookProgressId, BookSnapshot bookSnapshot, UUID userId, String type, int pageRead) {
        return ReadingSessionEntry.builder()
                .readingSessionId(bookProgressId)
                .type(type)
                .userId(userId)
                .bookSnapshot(bookSnapshot)
                .pageRead(pageRead)
                .build();
    }

}
