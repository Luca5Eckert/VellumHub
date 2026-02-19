package com.mrs.engagement_service.module.rating.application.handler;

import com.mrs.engagement_service.module.rating.application.dto.CreateRatingRequest;
import com.mrs.engagement_service.module.rating.domain.command.CreateRatingCommand;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.producer.CreatedRatingEventProducer;
import com.mrs.engagement_service.module.rating.domain.use_case.CreateRatingUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateRatingHandlerTest {

    @Mock
    private CreateRatingUseCase createRatingUseCase;

    @Mock
    private CreatedRatingEventProducer createdRatingEventProducer;

    @InjectMocks
    private CreateRatingHandler createRatingHandler;

    @Test
    @DisplayName("Should successfully save rating and produce Kafka event")
    void shouldHandleSuccessfully() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        CreateRatingRequest request = new CreateRatingRequest(bookId, 5, "Amazing book!");
        Rating mockRating = mock(Rating.class);

        when(createRatingUseCase.execute(any(CreateRatingCommand.class))).thenReturn(mockRating);

        // Act
        createRatingHandler.handle(request, userId);

        // Assert
        verify(createRatingUseCase, times(1)).execute(any(CreateRatingCommand.class));
        verify(createdRatingEventProducer, times(1)).produce(mockRating);
    }

    @Test
    @DisplayName("Should not produce Kafka event when database use case fails")
    void shouldFailWhenUseCaseThrowsException() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        CreateRatingRequest request = new CreateRatingRequest(bookId, 4, "Good read");

        when(createRatingUseCase.execute(any(CreateRatingCommand.class)))
                .thenThrow(new RuntimeException("Database connection error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            createRatingHandler.handle(request, userId);
        });

        verifyNoInteractions(createdRatingEventProducer);
    }

}