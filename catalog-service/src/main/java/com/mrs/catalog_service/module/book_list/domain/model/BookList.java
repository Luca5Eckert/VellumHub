package com.mrs.catalog_service.module.book_list.domain.model;

import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book_list.domain.exception.BookListDomainException;
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
        if (this.memberships == null) this.memberships = new ArrayList<>();
        if(isMember(userId)) return;

        BookListMembership membership = BookListMembership.create(this, userId, membershipRole);
        this.memberships.add(membership);
    }

    public void update(String title, String description, TypeBookList typeBookList) {
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        if (typeBookList != null) this.type = typeBookList;
    }

    public boolean canUpdate(UUID userId) {
        if (userOwner.equals(userId)) return true;

        return isAdmin(userId);
    }

    public boolean canRead(UUID userId) {
        if (type == TypeBookList.PUBLIC) return true;
        if (userId == null) return false;

        return userOwner.equals(userId) || isMember(userId);
    }

    public boolean isMember(UUID userId) {
        return memberships.stream()
                .anyMatch(m -> m.getUserId().equals(userId));
    }

    public boolean isAdmin(UUID userId) {
        return memberships.stream()
                .anyMatch(m -> m.getUserId().equals(userId) && m.getRole() == MembershipRole.ADMIN);
    }

    public boolean canDelete(UUID userId) {
        return userOwner.equals(userId);
    }

    public boolean canAddMember(UUID userId) {
        if(userOwner.equals(userId)) return true;
        if(type == TypeBookList.PRIVATE) return false;

        return isAdmin(userId);
    }

    public boolean canDeleteMember(UUID userId) {
        if(userOwner.equals(userId)) return true;
        if(type == TypeBookList.PRIVATE) return false;

        return isAdmin(userId);
    }

    public void deleteMember(UUID userId) {
        if(userId.equals(userOwner)) throw new BookListDomainException("Owner can't be removed from the book list");

        memberships.removeIf(m -> m.getUserId().equals(userId));
    }

    public boolean canUpdateRole(UUID user) {
        if(userOwner.equals(user)) return true;
        if(type == TypeBookList.PRIVATE) return false;

        return isAdmin(user);
    }
}

