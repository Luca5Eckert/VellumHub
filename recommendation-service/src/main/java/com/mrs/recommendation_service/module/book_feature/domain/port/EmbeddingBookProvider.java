package com.mrs.recommendation_service.module.book_feature.domain.port;

import com.mrs.recommendation_service.module.book_feature.domain.model.Genre;

import java.util.List;

public interface EmbeddingBookProvider {

    float[] of(String title, String author, String description, List<Genre> genres);

}
