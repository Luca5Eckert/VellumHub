package com.mrs.catalog_service.module.book_list.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "book_list_likes")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class BookListLike {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_list_id")
    private BookList bookList;

    @Column(name = "user_id")
    private UUID userId;

}
