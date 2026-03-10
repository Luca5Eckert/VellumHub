package com.mrs.catalog_service.module.book_list.domain.model;

import com.mrs.catalog_service.module.book.domain.model.Book;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "book_lists")
@Getter
@Setter
public class BookList {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "book_list_books",
            joinColumns = @JoinColumn(name = "book_list_id"),
            inverseJoinColumns = @JoinColumn(name = "book_id")
    )
    private List<Book> books;

    @OneToMany(mappedBy = "bookList", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookListMembership> memberships;

    private UUID userOwner;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public BookList() {
    }

}

