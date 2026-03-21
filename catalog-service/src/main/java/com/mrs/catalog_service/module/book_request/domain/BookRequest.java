package com.mrs.catalog_service.module.book_request.domain;

import com.mrs.catalog_service.module.book.domain.model.Genre;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "book_requests")
@Getter
@EntityListeners(AuditingEntityListener.class)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(length = 1000)
    private String description;

    @Column(
            nullable = false,
            name = "release_year"
    )
    private int releaseYear;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false)
    private String isbn;

    @Column(name = "page_count")
    private int pageCount;

    @Column(nullable = false)
    private String publisher;

    @Version
    private long version;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private Instant deletedAt;

    @ManyToMany
    @JoinTable(
            name = "book_request_genre_id",
            joinColumns = @JoinColumn(name = "book_request_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    @Builder.Default
    private Set<Genre> genres = new HashSet<>();

    public BookRequest(String title, String description, int releaseYear, String coverUrl, String author, String isbn, int pageCount, String publisher) {
        this.title = title;
        this.description = description;
        this.releaseYear = releaseYear;
        this.coverUrl = coverUrl;
        this.author = author;
        this.isbn = isbn;
        this.pageCount = pageCount;
        this.publisher = publisher;
    }

}
