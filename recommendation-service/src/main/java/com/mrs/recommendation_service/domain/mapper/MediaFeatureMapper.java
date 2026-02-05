package com.mrs.recommendation_service.domain.mapper;

import com.mrs.recommendation_service.domain.model.Genre;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class MediaFeatureMapper {

    public float[] mapToFeatureVector(List<Genre> genres){
        float[] vector = new float[Genre.total()];

        for (Genre genre : genres) {
                vector[genre.index] = 1.0f;
        }

        return vector;
    }

}
