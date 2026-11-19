package com.vellumhub.recommendation_service.module.book_event.application.use_case;

import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import com.vellumhub.recommendation_service.module.book_event.application.port.BookEventCheckpoint;
import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.EmbeddingBookProvider;
import com.vellumhub.recommendation_service.module.recommendation.domain.model.Recommendation;
import com.vellumhub.recommendation_service.module.recommendation.domain.port.RecommendationRepository;
import com.vellumhub.recommendation_service.share.application.EventProcessingOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/** Rebuilds Book read models exclusively from the complete Kafka state; transaction includes offset. */
@Slf4j
@Service
public class ApplyBookEventUseCase {

    private static final long NO_REVISION = -1;

    private final BookEventCheckpoint checkpoint;
    private final BookFeatureRepository features;
    private final EmbeddingBookProvider embeddings;
    private final RecommendationRepository recommendations;

    public ApplyBookEventUseCase(BookEventCheckpoint checkpoint,
                                 BookFeatureRepository features,
                                 EmbeddingBookProvider embeddings,
                                 RecommendationRepository recommendations) {
        this.checkpoint = checkpoint;
        this.features = features;
        this.embeddings = embeddings;
        this.recommendations = recommendations;
    }

    @Transactional
    public EventProcessingOutcome execute(BookChangedEvent event) {
        long previous = checkpoint.lockAndRead(event.bookId());
        if (event.revision() <= previous) {
            log.debug("operation=book_event_duplicate, bookId={}, revision={}", event.bookId(), event.revision());
            return EventProcessingOutcome.DUPLICATE;
        }
        verifyRevisionContinuity(event, previous);

        if (event.kind() == BookChangedEvent.Kind.DELETED) {
            features.deleteById(event.bookId());
            recommendations.deleteById(event.bookId());
        } else {
            applySnapshot(event);
        }
        checkpoint.applied(event.bookId(), previous, event.revision(), event.eventId());
        return EventProcessingOutcome.APPLIED;
    }

    /**
     * A BASELINE may only seed an empty checkpoint. Every other event must be exactly the next
     * revision, and the first non-baseline event of a book must be CREATED.
     */
    private static void verifyRevisionContinuity(BookChangedEvent event, long previous) {
        boolean seedingBaseline = event.kind() == BookChangedEvent.Kind.BASELINE && previous == NO_REVISION;
        if (seedingBaseline) {
            return;
        }
        if (event.revision() != previous + 1 || event.kind() == BookChangedEvent.Kind.BASELINE) {
            throw new IllegalStateException("Book revision gap: id=" + event.bookId()
                    + " expected=" + (previous + 1) + " actual=" + event.revision());
        }
        if (previous == NO_REVISION && event.kind() != BookChangedEvent.Kind.CREATED) {
            throw new IllegalStateException("Initial book event must be CREATED: " + event.bookId());
        }
    }

    private void applySnapshot(BookChangedEvent event) {
        BookChangedEvent.State state = event.state();
        Optional<Recommendation> existing = recommendations.findById(event.bookId());
        Optional<BookFeature> feature = features.findById(event.bookId());

        if (feature.isEmpty() || semanticContentChanged(existing, state)) {
            refreshEmbedding(event, state, feature);
        }

        Recommendation row = existing.orElseGet(() -> Recommendation.builder().bookId(event.bookId()).build());
        row.update(state.title(), state.description(), state.author(), state.coverUrl(),
                state.releaseYear(), state.genres());
        // update(...) intentionally skips nulls in legacy APIs; this ECST snapshot is authoritative.
        row.setDescription(state.description());
        row.setCoverUrl(state.coverUrl());
        recommendations.save(row);
    }

    /** Avoid expensive re-embedding for changes to cover, release year or publisher. */
    private static boolean semanticContentChanged(Optional<Recommendation> existing, BookChangedEvent.State state) {
        if (existing.isEmpty()) {
            return true;
        }
        Recommendation current = existing.get();
        return !Objects.equals(current.getTitle(), state.title())
                || !Objects.equals(current.getDescription(), state.description())
                || !Objects.equals(current.getAuthor(), state.author())
                || !Objects.equals(current.getGenres(), state.genres());
    }

    private void refreshEmbedding(BookChangedEvent event, BookChangedEvent.State state, Optional<BookFeature> feature) {
        var vector = embeddings.of(state.title(), state.author(),
                state.description() == null ? "" : state.description(), state.genres());
        BookFeature row = feature.orElseGet(() -> BookFeature.create(event.bookId(), vector, 1));
        if (feature.isPresent()) {
            row.updateEmbedding(vector);
        }
        features.save(row);
    }
}
