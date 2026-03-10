package com.mrs.catalog_service.module.book_list.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

}
