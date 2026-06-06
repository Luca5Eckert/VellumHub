package com.vellumhub.recommendation_service.module.book_feature.domain.port;

import java.util.List;

public interface EmbeddingBookProvider {

    float[] of(String title, String author, String description, List<String> genres);

}
