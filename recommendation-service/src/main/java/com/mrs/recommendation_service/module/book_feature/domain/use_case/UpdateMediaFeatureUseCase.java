package com.mrs.recommendation_service.module.book_feature.domain.use_case;

import com.mrs.recommendation_service.module.book_feature.domain.command.UpdateBookFeatureCommand;
import com.mrs.recommendation_service.module.book_feature.domain.exception.BookFeatureNotFoundException;
import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.mrs.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import org.springframework.stereotype.Component;

@Component
public class UpdateMediaFeatureUseCase {

    private final BookFeatureRepository bookFeatureRepository;

    public UpdateMediaFeatureUseCase(BookFeatureRepository bookFeatureRepository) {
        this.bookFeatureRepository = bookFeatureRepository;
    }

    public void execute(UpdateBookFeatureCommand command){
        BookFeature bookFeature = bookFeatureRepository.findById(command.mediaId())
                .orElseThrow(BookFeatureNotFoundException::new);

        bookFeature.update(command.genres());

        bookFeatureRepository.save(bookFeature);
    }

}
