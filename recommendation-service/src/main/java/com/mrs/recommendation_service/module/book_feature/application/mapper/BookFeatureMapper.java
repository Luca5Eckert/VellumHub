package com.mrs.recommendation_service.module.book_feature.application.mapper;

import com.mrs.recommendation_service.module.book_feature.domain.model.Genre;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class BookFeatureMapper {

    public float[] mapToFeatureVector(List<Genre> genres) {

        float[] vector = new float[Genre.total()];

        if (genres == null || genres.isEmpty()) {
            return vector;
        }

        for (Genre genre : genres) {
            if (genre.index < vector.length) {
                vector[genre.index] = 1.0f;
            } else {
                log.error("Gênero {} tem índice {} maior que o vetor suportado!",
                        genre.name(), genre.index);
            }
        }

        return vector;
    }

}