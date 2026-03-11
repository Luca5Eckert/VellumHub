package com.mrs.catalog_service.module.book_list.domain.model;

import com.mrs.catalog_service.module.book.domain.model.Book;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "book_lists")
@AllArgsConstructor
@Getter
@Setter
@Builder
public class BookList {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "book_list_books",
            joinColumns = @JoinColumn(name = "book_list_id"),
            inverseJoinColumns = @JoinColumn(name = "book_id")
    )
    private List<Book> books;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeBookList type;

    @OneToMany(mappedBy = "bookList", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BookListMembership> memberships = new ArrayList<>();

    @Column(
            nullable = false,
            name = "user_owner"
    )
    private UUID userOwner;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false
    )
    private Instant createdAt;

    @UpdateTimestamp
    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    public BookList() {
    }

    public static BookList create(String title, String description, TypeBookList type, UUID userOwner, List<Book> books) {
        BookList bookList = BookList.builder()
                .title(title)
                .description(description)
                .type(type)
                .userOwner(userOwner)
                .books(books)
                .memberships(new ArrayList<>())
                .build();

        bookList.addMember(userOwner, MembershipRole.OWNER);

        return bookList;
    }

    public void addMember(UUID userId, MembershipRole membershipRole) {
        if (this.memberships == null) {
            this.memberships = new ArrayList<>();
        }

        BookListMembership membership = BookListMembership.create(this, userId, membershipRole);
        this.memberships.add(membership);
    }

    public void update(String name, String description, TypeBookList typeBookList) {
        if(name != null) {
            this.title = name;
        }
        if(description != null) {
            this.description = description;
        }
        if(typeBookList != null){
            this.type = typeBookList;
        }
    }
}

