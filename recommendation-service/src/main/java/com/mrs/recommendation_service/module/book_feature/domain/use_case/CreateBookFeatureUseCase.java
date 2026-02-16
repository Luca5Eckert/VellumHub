package com.mrs.recommendation_service.module.book_feature.domain.use_case;

import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.mrs.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import org.springframework.stereotype.Component;

@Component
public class CreateBookFeatureUseCase {

    private final BookFeatureRepository bookFeatureRepository;

    public CreateBookFeatureUseCase(BookFeatureRepository bookFeatureRepository) {
        this.bookFeatureRepository = bookFeatureRepository;
    }

    public void execute(BookFeature bookFeature){
        bookFeatureRepository.save(bookFeature);
    }

}
