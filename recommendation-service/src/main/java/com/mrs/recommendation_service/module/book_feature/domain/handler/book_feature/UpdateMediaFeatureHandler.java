package com.mrs.recommendation_service.module.book_feature.domain.handler.book_feature;

import com.mrs.recommendation_service.module.book_feature.domain.command.UpdateBookFeatureCommand;
import com.mrs.recommendation_service.module.book_feature.domain.exception.book_feature.BookFeatureNotFoundException;
import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.mrs.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import org.springframework.stereotype.Component;

@Component
public class UpdateMediaFeatureHandler {

    private final BookFeatureRepository bookFeatureRepository;

    public UpdateMediaFeatureHandler(BookFeatureRepository bookFeatureRepository) {
        this.bookFeatureRepository = bookFeatureRepository;
    }

    public void execute(UpdateBookFeatureCommand command){
        BookFeature bookFeature = bookFeatureRepository.findById(command.mediaId())
                .orElseThrow(BookFeatureNotFoundException::new);

        bookFeature.update(command.genres());

        bookFeatureRepository.save(bookFeature);
    }

}
