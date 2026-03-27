package com.vellumhub.catalog_service.module.book.domain.handler;

import com.vellumhub.catalog_service.module.book.domain.event.UpdateBookEvent;
import com.vellumhub.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.vellumhub.catalog_service.module.book.domain.exception.InvalidBookException;
import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.model.Genre;
import com.vellumhub.catalog_service.module.book.domain.port.BookEventProducer;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import com.vellumhub.catalog_service.module.book.domain.port.GenreRepository;
import com.vellumhub.catalog_service.module.book.presentation.dto.UpdateBookRequest;
import com.vellumhub.catalog_service.module.book_request.domain.exception.BookRequestDomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
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
    private GenreRepository genreRepository;

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
        @DisplayName("Should successfully update book, resolve genres and send event")
        void shouldUpdateAndSendEvent() {
            // Given
            var request = createRequest("New Title", "978-123", Set.of("Sci-Fi"));
            var mockGenre = mock(Genre.class);

            given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
            given(book.getTitle()).willReturn("Old Title");
            given(genreRepository.findByName("Sci-Fi")).willReturn(Optional.of(mockGenre));

            // Stub for event creation inside the handler
            given(book.getGenres()).willReturn(Set.of(mockGenre));
            given(mockGenre.getName()).willReturn("Sci-Fi");

            // When
            updateBookHandler.execute(bookId, request);

            // Then
            then(book).should().update(
                    eq("New Title"), eq("Desc"), eq("url"), eq(2024),
                    eq("Author"), eq("978-123"), eq(100), eq("Publisher"), eq(Set.of(mockGenre))
            );
            then(bookRepository).should().save(book);

            var eventCaptor = ArgumentCaptor.forClass(UpdateBookEvent.class);
            then(bookEventProducer).should().send(eq("updated-book"), eq(bookId.toString()), eventCaptor.capture());

            assertThat(eventCaptor.getValue().genres()).containsExactly("Sci-Fi");
        }

        @Test
        @DisplayName("Should process successfully when genre list is empty")
        void shouldProcessWithEmptyGenres() {
            // Given
            var request = createRequest("New Title", "978-123", Set.of());
            given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
            given(book.getTitle()).willReturn("Old Title");
            given(book.getGenres()).willReturn(Set.of()); // Returns empty set for the event

            // When
            updateBookHandler.execute(bookId, request);

            // Then
            then(genreRepository).shouldHaveNoInteractions();
            then(bookRepository).should().save(book);
            then(bookEventProducer).should().send(eq("updated-book"), eq(bookId.toString()), any(UpdateBookEvent.class));
        }
    }

    @Nested
    @DisplayName("Identity & Duplicate Validation")
    class ValidationLogic {

        @Test
        @DisplayName("Should NOT call database exists check if Title and ISBN haven't changed")
        void shouldShortCircuitValidation() {
            // Given
            var request = createRequest("Same Title", "Same ISBN", Set.of());
            given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
            given(book.getTitle()).willReturn("Same Title");
            given(book.getIsbn()).willReturn("Same ISBN");
            given(book.getGenres()).willReturn(Set.of());

            // When
            updateBookHandler.execute(bookId, request);

            // Then
            then(bookRepository).should(never()).existByTitleAndAuthorAndIsbn(any(), any(), any());
        }

        @Test
        @DisplayName("Should throw InvalidBookException when updated title/author/isbn already exists")
        void shouldThrowExceptionOnDuplicate() {
            // Given
            var request = createRequest("New Title", "New ISBN", Set.of());
            given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
            given(book.getTitle()).willReturn("Old Title");
            given(bookRepository.existByTitleAndAuthorAndIsbn(any(), any(), any())).willReturn(true);

            // When / Then
            assertThatThrownBy(() -> updateBookHandler.execute(bookId, request))
                    .isInstanceOf(InvalidBookException.class)
                    .hasMessageContaining("already exists");

            then(bookRepository).should(never()).save(any());
            then(bookEventProducer).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should throw BookNotFoundException when ID is invalid")
        void shouldThrowNotFound() {
            given(bookRepository.findById(bookId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> updateBookHandler.execute(bookId, createRequest("T", "I", Set.of())))
                    .isInstanceOf(BookNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw BookRequestDomainException when genre does not exist")
        void shouldThrowExceptionWhenGenreNotFound() {
            // Given
            var request = createRequest("Title", "ISBN", Set.of("Unknown Genre"));
            given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
            given(genreRepository.findByName("Unknown Genre")).willReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> updateBookHandler.execute(bookId, request))
                    .isInstanceOf(BookRequestDomainException.class)
                    .hasMessageContaining("Genre not found: Unknown Genre");

            then(bookRepository).should(never()).save(any());
        }
    }

    private UpdateBookRequest createRequest(String title, String isbn, Set<String> genres) {
        return new UpdateBookRequest(
                title, "Desc", 2024, "url", "Author", isbn, 100, "Publisher", genres
        );
    }
}