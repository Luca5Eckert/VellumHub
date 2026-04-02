package com.vellumhub.recommendation_service.module.book_feature.application.use_case;

import com.vellumhub.recommendation_service.module.book_feature.application.command.UpdateBookFeatureCommand;
import com.vellumhub.recommendation_service.module.book_feature.domain.exception.BookFeatureNotFoundException;
import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.EmbeddingBookProvider;
import org.springframework.stereotype.Component;

@Component
public class UpdateBookFeatureUseCase {

    private final BookFeatureRepository bookFeatureRepository;
    private final EmbeddingBookProvider embeddingBookProvider;

    public UpdateBookFeatureUseCase(BookFeatureRepository bookFeatureRepository, EmbeddingBookProvider embeddingBookProvider) {
        this.bookFeatureRepository = bookFeatureRepository;
        this.embeddingBookProvider = embeddingBookProvider;
    }

    public void execute(UpdateBookFeatureCommand command){
        BookFeature bookFeature = bookFeatureRepository.findById(command.bookId())
                .orElseThrow(BookFeatureNotFoundException::new);

        var vectors = embeddingBookProvider.of(
                command.title(),
                command.author(),
                command.description(),
                command.genres()
        );

        bookFeature.updateEmbedding(vectors);

        bookFeatureRepository.save(bookFeature);
    }

}
