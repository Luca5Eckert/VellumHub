package com.vellumhub.engagement_service.module.rating.domain.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import com.vellumhub.engagement_service.module.rating.domain.command.CreateRatingCommand;
import com.vellumhub.engagement_service.module.rating.domain.exception.RatingAlreadyExistException;
import com.vellumhub.engagement_service.module.rating.domain.model.Rating;
import com.vellumhub.engagement_service.module.rating.domain.port.RatingRepository;
import com.vellumhub.engagement_service.share.metrics.VellumHubMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateRatingUseCaseTest {

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private BookSnapshotRepository bookSnapshotRepository;

    private CreateRatingUseCase createRatingUseCase;

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        createRatingUseCase = new CreateRatingUseCase(
                ratingRepository,
                bookSnapshotRepository,
                new VellumHubMetrics(meterRegistry)
        );
    }

    @Test
    @DisplayName("Should create rating successfully when it does not already exist")
    void shouldCreateRatingSuccessfully() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        CreateRatingCommand command = new CreateRatingCommand(userId, bookId, 4, "Great book!");

        when(bookSnapshotRepository.existsById(bookId)).thenReturn(true);
        when(ratingRepository.existsByUserIdAndBookId(userId, bookId)).thenReturn(false);
        when(ratingRepository.save(any(Rating.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Rating result = createRatingUseCase.execute(command);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getBookId()).isEqualTo(bookId);
        assertThat(result.getStars()).isEqualTo(4);
        assertThat(result.getReview()).isEqualTo("Great book!");

        ArgumentCaptor<Rating> captor = ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getTimestamp()).isNotNull();
        assertThat(ratingsCreatedCount()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should throw RatingAlreadyExistException when rating already exists")
    void shouldThrowExceptionWhenRatingAlreadyExists() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        CreateRatingCommand command = new CreateRatingCommand(userId, bookId, 3, "OK");

        when(bookSnapshotRepository.existsById(bookId)).thenReturn(true);
        when(ratingRepository.existsByUserIdAndBookId(userId, bookId)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> createRatingUseCase.execute(command))
                .isInstanceOf(RatingAlreadyExistException.class);

        verify(ratingRepository, never()).save(any());
    }

    private double ratingsCreatedCount() {
        return meterRegistry.get(VellumHubMetrics.RATINGS_CREATED)
                .tag("operation", "rating_creation")
                .tag("result", "success")
                .counter()
                .count();
    }
}
