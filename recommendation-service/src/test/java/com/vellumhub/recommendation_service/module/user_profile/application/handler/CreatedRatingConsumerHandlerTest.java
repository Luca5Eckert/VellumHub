package com.vellumhub.recommendation_service.module.user_profile.application.handler;

import com.vellumhub.recommendation_service.module.user_profile.presentation.event.CreatedRatingEvent;
import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateUserProfileWithRatingCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.UpdateUserProfileWithRatingUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatedRatingConsumerHandlerTest {

    @Mock
    private UpdateUserProfileWithRatingUseCase updateUserProfileWithRatingUseCase;

    @InjectMocks
    private CreatedRatingConsumerHandler createdRatingConsumerHandler;

    @Test
    @DisplayName("Should call use case with correct command when handling rating event")
    void shouldCallUseCaseWithCorrectCommand() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        CreatedRatingEvent event = new CreatedRatingEvent(userId, bookId, 5);

        // Act
        createdRatingConsumerHandler.handle(event);

        // Assert
        ArgumentCaptor<UpdateUserProfileWithRatingCommand> captor =
                ArgumentCaptor.forClass(UpdateUserProfileWithRatingCommand.class);
        verify(updateUserProfileWithRatingUseCase, times(1)).execute(captor.capture());

        UpdateUserProfileWithRatingCommand capturedCommand = captor.getValue();
        assertThat(capturedCommand.userId()).isEqualTo(userId);
        assertThat(capturedCommand.bookId()).isEqualTo(bookId);
        assertThat(capturedCommand.newStars()).isEqualTo(5);
        assertThat(capturedCommand.oldStars()).isZero();
        assertThat(capturedCommand.isNewRating()).isFalse(); // handler hardcodes false for isNewRating
    }

    @Test
    @DisplayName("Should propagate exception when use case fails")
    void shouldPropagateExceptionWhenUseCaseFails() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        CreatedRatingEvent event = new CreatedRatingEvent(userId, bookId, 3);

        doThrow(new RuntimeException("Profile update failed"))
                .when(updateUserProfileWithRatingUseCase).execute(any());

        // Act & Assert
        assertThatThrownBy(() -> createdRatingConsumerHandler.handle(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Profile update failed");
    }
}
