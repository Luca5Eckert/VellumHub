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

    public void update(String title, String description, String author, String coverUrl, int releaseYear, List<Genre> genres) {
        if(title != null) {
            this.title = title;
        }
        if(description != null) {
            this.description = description;
        }
        if(author != null) {
            this.author = author;
        }
        if(coverUrl != null) {
            this.coverUrl = coverUrl;
        }
        if(releaseYear != 0) {
            this.releaseYear = releaseYear;
        }
        if(genres != null) {
            this.genres = genres;
        }
    }

}
