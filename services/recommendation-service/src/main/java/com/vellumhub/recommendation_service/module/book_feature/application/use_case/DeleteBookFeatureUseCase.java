package com.vellumhub.recommendation_service.module.book_feature.application.use_case;

import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DeleteBookFeatureUseCase {

    private final BookFeatureRepository bookFeatureRepository;

    public DeleteBookFeatureUseCase(BookFeatureRepository bookFeatureRepository) {
        this.bookFeatureRepository = bookFeatureRepository;
    }

    public void execute(UUID bookId){
        bookFeatureRepository.deleteById(bookId);
    }

}
