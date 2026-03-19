package com.mrs.catalog_service.module.book.domain.model;

import io.swagger.v3.core.converter.AnnotatedType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "genres")
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Genre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false, unique = true)
    private String name;


    public Genre(String normalizedName) {
        this.name = normalizedName;
    }
}