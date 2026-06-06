package com.vellumhub.catalog_service.module.book_request.domain.use_case;

import com.vellumhub.catalog_service.module.book_request.domain.command.DeleteBookRequestCommand;
import com.vellumhub.catalog_service.module.book_request.domain.exception.BookRequestNotFoundException;
import com.vellumhub.catalog_service.module.book_request.domain.port.BookRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteBookRequestUseCase Unit Tests")
class DeleteBookRequestUseCaseTest {

    @Mock
    private BookRequestRepository bookRequestRepository;

    @InjectMocks
    private DeleteBookRequestUseCase deleteBookRequestUseCase;

    @Nested
    @DisplayName("Success scenarios")
    class SuccessScenarios {

        @Test
        @DisplayName("Should delete book request when it exists")
        void shouldDeleteBookRequestWhenExists() {
            // Given
            long requestId = 1L;
            DeleteBookRequestCommand command = new DeleteBookRequestCommand(requestId);
            
            given(bookRequestRepository.existsById(requestId)).willReturn(true);

            // When
            deleteBookRequestUseCase.execute(command);

            // Then
            then(bookRequestRepository).should().existsById(requestId);
            then(bookRequestRepository).should().deleteById(requestId);
        }
    }

    @Nested
    @DisplayName("Failure scenarios")
    class FailureScenarios {

        @Test
        @DisplayName("Should throw exception when book request does not exist")
        void shouldThrowExceptionWhenRequestDoesNotExist() {
            // Given
            long requestId = 999L;
            DeleteBookRequestCommand command = new DeleteBookRequestCommand(requestId);
            
            given(bookRequestRepository.existsById(requestId)).willReturn(false);

            // When / Then
            assertThatThrownBy(() -> deleteBookRequestUseCase.execute(command))
                    .isInstanceOf(BookRequestNotFoundException.class);

            then(bookRequestRepository).should(never()).deleteById(requestId);
        }
    }
}
