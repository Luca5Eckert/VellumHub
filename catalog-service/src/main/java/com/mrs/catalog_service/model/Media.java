package com.mrs.catalog_service.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "medias")
@Data
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    private String title;

    private String description;

    private int releaseYear;

    @Enumerated(EnumType.STRING)
    private MediaType mediaType;

    private String coverUrl;

    @Version
    private long version;

    private Instant createAt;

    private Instant updateAt;

    private Instant deletedAt;

    @ElementCollection
    @CollectionTable(
            name = "tb_media_genre",
            joinColumns = @JoinColumn(name = "media_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "genre_name")
    private List<Genre> genres;

    public Media() {
    }

    public Media(Builder builder) {
        this.title = builder.title;
        this.description = builder.description;
        this.releaseYear = builder.releaseYear;
        this.mediaType = builder.mediaType;
        this.coverUrl = builder.coverUrl;
        this.createAt = builder.createAt;
        this.updateAt = builder.updateAt;
        this.deletedAt = null;
        this.genres = builder.genres;
    }

    public static class Builder {

        private String title;
        private String description;
        private MediaType mediaType;
        private int releaseYear;
        private String coverUrl;
        private Instant createAt;
        private Instant updateAt;
        private List<Genre> genres;

        public Builder() {
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder mediaType(MediaType mediaType) {
            this.mediaType = mediaType;
            return this;
        }


        public Builder releaseYear(int releaseYear) {
            this.releaseYear = releaseYear;
            return this;
        }

        public Builder coverUrl(String coverUrl) {
            this.coverUrl = coverUrl;
            return this;
        }

        public Builder createAt(Instant createAt) {
            this.createAt = createAt;
            return this;
        }

        public Builder updateAt(Instant updateAt) {
            this.updateAt = updateAt;
            return this;
        }

        public Builder genres(List<Genre> genres) {
            this.genres = genres;
            return this;
        }

        public Media build() {
            return new Media(this);
        }


    }

}
