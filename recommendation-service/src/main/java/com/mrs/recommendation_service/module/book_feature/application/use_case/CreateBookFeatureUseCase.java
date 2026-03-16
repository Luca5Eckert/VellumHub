package com.mrs.recommendation_service.module.book_feature.application.use_case;

import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.mrs.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.mrs.recommendation_service.share.event.CreateBookEvent;
import org.springframework.stereotype.Component;

@Component
public class CreateBookFeatureUseCase {

    private final BookFeatureRepository bookFeatureRepository;

    public CreateBookFeatureUseCase(BookFeatureRepository bookFeatureRepository) {
        this.bookFeatureRepository = bookFeatureRepository;
    }

    public void execute(CreateBookEvent event){
        BookFeature bookFeature = BookFeature.of(event.bookId(), event.genres());

        bookFeatureRepository.save(bookFeature);
    }

}
