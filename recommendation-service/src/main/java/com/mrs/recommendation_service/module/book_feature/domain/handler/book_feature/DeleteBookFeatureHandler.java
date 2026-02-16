package com.mrs.recommendation_service.module.book_feature.domain.handler.book_feature;

import com.mrs.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DeleteBookFeatureHandler {

    private final BookFeatureRepository bookFeatureRepository;

    public DeleteBookFeatureHandler(BookFeatureRepository bookFeatureRepository) {
        this.bookFeatureRepository = bookFeatureRepository;
    }

    public void execute(UUID mediaId){
        bookFeatureRepository.deleteById(mediaId);
    }

}
