package com.mrs.engagement_service.module.book_snapshot.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "book_snapshot")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BookSnapshot {

    @Id
    @Column(nullable = false)
    private UUID bookId;

}
