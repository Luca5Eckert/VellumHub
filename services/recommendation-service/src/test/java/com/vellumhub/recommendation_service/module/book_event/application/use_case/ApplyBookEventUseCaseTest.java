package com.vellumhub.recommendation_service.module.book_event.application.use_case;

import com.vellumhub.recommendation_service.module.book_event.application.port.BookEventCheckpoint;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.EmbeddingBookProvider;
import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.recommendation.domain.port.RecommendationRepository;
import com.vellumhub.recommendation_service.module.recommendation.domain.model.Recommendation;

class ApplyBookEventUseCaseTest {
    private final BookEventCheckpoint checkpoint=mock(BookEventCheckpoint.class);
    private final BookFeatureRepository features=mock(BookFeatureRepository.class);
    private final EmbeddingBookProvider embeddings=mock(EmbeddingBookProvider.class);
    private final RecommendationRepository recommendations=mock(RecommendationRepository.class);
    private final ApplyBookEventUseCase process=new ApplyBookEventUseCase(
        checkpoint, features, embeddings, recommendations);

    private BookChangedEvent event(UUID id,long rev,BookChangedEvent.Kind kind,String cover) {
        var state=kind==BookChangedEvent.Kind.DELETED?null:
            new BookChangedEvent.State("Dune","SF",1965,cover,"Herbert","ISBN",400,"Ace",List.of("SF"));
        return new BookChangedEvent(UUID.randomUUID(),Instant.now(),id,rev,kind,state);
    }

    @Test void baselineRebuildsLocalModelsWithoutCatalogHttpCalls() {
        UUID id=UUID.randomUUID();
        when(checkpoint.lockAndRead(id)).thenReturn(-1L);
        when(embeddings.of(any(),any(),any(),any())).thenReturn(new float[384]);
        process.execute(event(id,6,BookChangedEvent.Kind.BASELINE,null));
        verify(features).save(any(BookFeature.class));
        verify(recommendations).save(any(Recommendation.class));
        verify(checkpoint).applied(id,-1,6);
    }

    @Test void duplicateCannotRedoEmbedding() {
        UUID id=UUID.randomUUID();
        when(checkpoint.lockAndRead(id)).thenReturn(2L);
        process.execute(event(id,2,BookChangedEvent.Kind.UPDATED,"/cover.png"));
        verifyNoInteractions(embeddings,features,recommendations);
    }

    @Test void gapFailsWithoutAdvancing() {
        UUID id=UUID.randomUUID();
        when(checkpoint.lockAndRead(id)).thenReturn(0L);
        assertThatThrownBy(()->process.execute(event(id,3,BookChangedEvent.Kind.UPDATED,null)))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(embeddings,features,recommendations);
        verify(checkpoint,never()).applied(any(),anyLong(),anyLong());
    }

    @Test void coverOnlyChangeDoesNotRecomputeVector() {
        UUID id=UUID.randomUUID();
        when(checkpoint.lockAndRead(id)).thenReturn(0L);
        Recommendation old=Recommendation.builder().bookId(id).title("Dune")
            .description("SF").releaseYear(1965).author("Herbert")
            .coverUrl("/old.png").genres(List.of("SF")).build();
        when(recommendations.findById(id)).thenReturn(Optional.of(old));
        when(features.findById(id)).thenReturn(Optional.of(BookFeature.create(id,new float[384],1)));
        process.execute(event(id,1,BookChangedEvent.Kind.UPDATED,"/new.png"));
        verifyNoInteractions(embeddings);
        assertThat(old.getCoverUrl()).isEqualTo("/new.png");
        verify(checkpoint).applied(id,0,1);
    }

    @Test void deletionClearsReadModelsOnce() {
        UUID id=UUID.randomUUID();
        when(checkpoint.lockAndRead(id)).thenReturn(1L);
        process.execute(event(id,2,BookChangedEvent.Kind.DELETED,null));
        verify(features).deleteById(id);
        verify(recommendations).deleteById(id);
        verify(checkpoint).applied(id,1,2);
    }
}

