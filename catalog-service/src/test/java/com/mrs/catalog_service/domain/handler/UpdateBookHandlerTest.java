package com.mrs.catalog_service.module.book.domain.handler;

import com.mrs.catalog_service.module.book.application.dto.UpdateBookRequest;
import com.mrs.catalog_service.module.book.domain.event.UpdateBookEvent;
import com.mrs.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.mrs.catalog_service.module.book.domain.exception.InvalidBookException;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.model.Genre;
import com.mrs.catalog_service.module.book.domain.port.BookEventProducer;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateBookHandler Unit Tests")
class UpdateBookHandlerTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookEventProducer<String, UpdateBookEvent> bookEventProducer;

    @InjectMocks
    private UpdateBookHandler updateBookHandler;

    private final UUID bookId = UUID.randomUUID();
    private Book book;

    @BeforeEach
    void setUp() {
        book = mock(Book.class);
        lenient().when(book.getId()).thenReturn(bookId);
    }

    @Nested
    @DisplayName("Execution Logic & Event Emission")
    class ExecutionLogic {

        @Test
        @DisplayName("Should successfully update book and send event when genres are present")
        void shouldUpdateAndSendEvent() {
            // Given
            var request = createRequest("New Title", "978-123", List.of(Genre.SCI_FI));
            given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
            given(book.getTitle()).willReturn("Old Title");

            // When
            updateBookHandler.execute(bookId, request);

            // Then
            then(book).should().update(any(), any(), any(), anyInt(), any(), any(), anyInt(), any(), any());
            then(bookRepository).should().save(book);

            var eventCaptor = ArgumentCaptor.forClass(UpdateBookEvent.class);
            then(bookEventProducer).should().send(eq("updated-book"), eq(bookId.toString()), eventCaptor.capture());
            assertThat(eventCaptor.getValue().genres()).containsExactly(Genre.SCI_FI);
        }

        @Test
        @DisplayName("Should update book but NOT send event when genres are null")
        void shouldUpdateWithoutEventWhenGenresNull() {
            // Given
            var request = createRequest("New Title", "978-123", null);
            given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
            given(book.getTitle()).willReturn("Old Title");

            // When
            updateBookHandler.execute(bookId, request);

            // Then
            then(bookRepository).should().save(book);
            then(bookEventProducer).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("Identity & Duplicate Validation")
    class ValidationLogic {

        @Test
        @DisplayName("Should NOT call database exists check if Title and ISBN haven't changed")
        void shouldShortCircuitValidation() {
            // Given
            var request = createRequest("Same Title", "Same ISBN", null);
            given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
            given(book.getTitle()).willReturn("Same Title");
            given(book.getIsbn()).willReturn("Same ISBN");

            // When
            updateBookHandler.execute(bookId, request);

            // Then
            then(bookRepository).should(never()).existByTitleAndAuthorAndIsbn(any(), any(), any());
        }

        @Test
        @DisplayName("Should throw InvalidBookException when updated title/author/isbn already exists")
        void shouldThrowExceptionOnDuplicate() {
            var request = createRequest("New Title", "New ISBN", null);
            given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
            given(book.getTitle()).willReturn("Old Title");
            given(bookRepository.existByTitleAndAuthorAndIsbn(any(), any(), any())).willReturn(true);

            assertThatThrownBy(() -> updateBookHandler.execute(bookId, request))
                    .isInstanceOf(InvalidBookException.class)
                    .hasMessageContaining("already exists");

            then(bookRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should throw BookNotFoundException when ID is invalid")
        void shouldThrowNotFound() {
            given(bookRepository.findById(bookId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> updateBookHandler.execute(bookId, createRequest("T", "I", null)))
                    .isInstanceOf(BookNotFoundException.class);
        }
    }

    private UpdateBookRequest createRequest(String title, String isbn, List<Genre> genres) {
        return new UpdateBookRequest(
                title, "Desc", 2024, "url", "Author", isbn, 100, "Publisher", genres
        );
    }
}