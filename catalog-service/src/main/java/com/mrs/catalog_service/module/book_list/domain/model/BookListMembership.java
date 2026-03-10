package com.mrs.catalog_service.module.book_list.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "book_list_memberships")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookListMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_list_id")
    private BookList bookList;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    private MembershipRole role;

    private boolean isFavorite;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public static BookListMembership create(BookList bookList, UUID userId, MembershipRole role, boolean isFavorite) {
        return BookListMembership.builder()
                .bookList(bookList)
                .userId(userId)
                .role(role)
                .isFavorite(isFavorite)
                .build();
    }

}
