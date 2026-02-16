package com.mrs.recommendation_service.module.book_feature.domain.handler.book_feature;

import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.mrs.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import org.springframework.stereotype.Component;

@Component
public class CreateBookFeatureHandler {

    private final BookFeatureRepository bookFeatureRepository;

    public CreateBookFeatureHandler(BookFeatureRepository bookFeatureRepository) {
        this.bookFeatureRepository = bookFeatureRepository;
    }

    public void execute(BookFeature bookFeature){
        bookFeatureRepository.save(bookFeature);
    }

}
