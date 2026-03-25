package com.mrs.catalog_service.module.book.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "books")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamicUpdate
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditingEntityListener.class)
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
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
            name = "book_genre_id",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private Set<Genre> genres;

    public static Book create(String title, String description, int releaseYear, String author,
                              String isbn, int pageCount, String publisher, String coverUrl,
                              Set<Genre> resolvedGenres) {
        return Book.builder()
                .title(title)
                .description(description)
                .releaseYear(releaseYear)
                .author(author)
                .isbn(isbn)
                .pageCount(pageCount)
                .publisher(publisher)
                .coverUrl(coverUrl)
                .genres(resolvedGenres)
                .build();
    }

    /**
     * Domain method to update the entity.
     * Ensures all business rules are validated before state change.
     */
    public void update(
            String title,
            String description,
            String coverUrl,
            Integer releaseYear,
            String author,
            String isbn,
            Integer pageCount,
            String publisher,
            Set<Genre> genres
    ) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }

        if (description != null && !description.isBlank()) {
            this.description = description;
        }

        if (coverUrl != null && !coverUrl.isBlank()) {
            this.coverUrl = coverUrl;
        }

        if (releaseYear != null && releaseYear > 0) {
            this.releaseYear = releaseYear;
        }

        if (author != null && !author.isBlank()) {
            this.author = author;
        }

        if (isbn != null && !isbn.isBlank()) {
            this.isbn = isbn;
        }

        if (pageCount != null && pageCount > 0) {
            this.pageCount = pageCount;
        }

        if (publisher != null && !publisher.isBlank()) {
            this.publisher = publisher;
        }

        if (genres != null && !genres.isEmpty()) {
            this.genres = new HashSet<>(genres);
        }
    }

    /**
     * Domain method to update only the cover URL.
     */
    public void updateCoverUrl(String coverUrl) {
        if (coverUrl != null && !coverUrl.isBlank()) {
            this.coverUrl = coverUrl;
        }
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Book book = (Book) o;
        return getId() != null && Objects.equals(getId(), book.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}