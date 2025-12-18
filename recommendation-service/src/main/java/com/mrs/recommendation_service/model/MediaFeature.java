package com.mrs.recommendation_service.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "medias_features")
@Data
public class MediaFeature {

    @Id
    private UUID mediaId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "genres", columnDefinition = "text[]")
    private List<String> genres;

    private double popularityScore;

    public MediaFeature(){
    }

    public MediaFeature(UUID mediaId, List<String> genres){
        this.mediaId = mediaId;
        this.genres = genres;
    }

}