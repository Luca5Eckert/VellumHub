package com.mrs.recommendation_service.module.recommendation.domain.model;

import com.mrs.recommendation_service.module.book_feature.domain.model.Genre;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name ="recommendations"
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Recommendation {

    @Id
    private UUID bookId;

    @Column(
            nullable = false
    )
    private String title;

    @Column(
            nullable = false
    )
    private String description;

    @Column(
            nullable = false
    )
    private int releaseYear;

    @Column(
            nullable = false
    )
    private String coverUrl;

    @Column(
            nullable = false
    )
    private String author;

    @Column(
            nullable = false
    )
    private List<Genre> genres;

}
