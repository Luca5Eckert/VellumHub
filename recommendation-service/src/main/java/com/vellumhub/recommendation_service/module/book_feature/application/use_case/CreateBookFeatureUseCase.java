package com.vellumhub.recommendation_service.module.book_feature.application.use_case;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.EmbeddingBookProvider;
import com.vellumhub.recommendation_service.share.event.CreateBookEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateBookFeatureUseCase {

    private final BookFeatureRepository bookFeatureRepository;
    private final EmbeddingBookProvider embeddingBookProvider;

    public CreateBookFeatureUseCase(BookFeatureRepository bookFeatureRepository, EmbeddingBookProvider embeddingBookProvider) {
        this.bookFeatureRepository = bookFeatureRepository;
        this.embeddingBookProvider = embeddingBookProvider;
    }

    @Transactional
    public void execute(CreateBookEvent event){
        var vectors = embeddingBookProvider.of(
                event.title(),
                event.author(),
                event.description(),
                event.genres()
        );

        BookFeature bookFeature = BookFeature.create(event.bookId(), vectors, 1);

        bookFeatureRepository.save(bookFeature);
    }

}
