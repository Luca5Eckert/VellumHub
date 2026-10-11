package com.vellumhub.engagement_service.module.book_snapshot.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "book_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class BookSnapshot {

    @Id
    @Column(nullable = false)
    private UUID bookId;

    /** A deleted Catalog book remains as a local identity to preserve historical foreign keys. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    public BookSnapshot(UUID bookId) {
        this.bookId = bookId;
        this.active = true;
    }

}
