package com.mrs.catalog_service.module.book.domain.handler;

import com.mrs.catalog_service.module.book.application.command.CreateBookCommand;
import com.mrs.catalog_service.module.book.domain.event.CreateBookEvent;
import com.mrs.catalog_service.module.book.domain.exception.InvalidBookException;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.model.Genre;
import com.mrs.catalog_service.module.book.domain.port.BookEventProducer;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import com.mrs.catalog_service.module.book.domain.port.GenreRepository;
import com.mrs.catalog_service.module.book_request.domain.exception.BookRequestDomainException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateBookHandler Unit Tests")
class CreateBookHandlerTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private BookEventProducer<String, CreateBookEvent> bookEventProducer;

    @InjectMocks
    private CreateBookHandler createBookHandler;

    @Nested
    @DisplayName("Success Scenarios")
    class SuccessScenarios {

        @Test
        @DisplayName("Should successfully create book, map genres and publish event")
        void shouldCreateBookSuccessfully() {
            // Given
            var command = new CreateBookCommand(
                    "The Hobbit", "A great adventure", 1937, "J.R.R. Tolkien", "123456789",
                    310, "Allen & Unwin", "http://cover.url", Set.of("Fantasy", "Adventure")
            );

            var fantasyGenre = mock(Genre.class);
            given(fantasyGenre.getName()).willReturn("Fantasy");

            var adventureGenre = mock(Genre.class);
            given(adventureGenre.getName()).willReturn("Adventure");

            given(bookRepository.existByTitleAndAuthorAndIsbn("The Hobbit", "J.R.R. Tolkien", "123456789"))
                    .willReturn(false);

            given(genreRepository.findByName("Fantasy")).willReturn(Optional.of(fantasyGenre));
            given(genreRepository.findByName("Adventure")).willReturn(Optional.of(adventureGenre));

            // When
            createBookHandler.execute(command);

            // Then
            var bookCaptor = ArgumentCaptor.forClass(Book.class);
            then(bookRepository).should().save(bookCaptor.capture());

            Book savedBook = bookCaptor.getValue();
            assertThat(savedBook.getTitle()).isEqualTo("The Hobbit");
            assertThat(savedBook.getAuthor()).isEqualTo("J.R.R. Tolkien");
            assertThat(savedBook.getIsbn()).isEqualTo("123456789");
            assertThat(savedBook.getGenres()).containsExactlyInAnyOrder(fantasyGenre, adventureGenre);

            var eventCaptor = ArgumentCaptor.forClass(CreateBookEvent.class);
            then(bookEventProducer).should().send(
                    eq("created-book"),
                    anyString(), // Key usually maps to bookId, which is null until DB generates it
                    eventCaptor.capture()
            );

            CreateBookEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.title()).isEqualTo("The Hobbit");
            assertThat(capturedEvent.author()).isEqualTo("J.R.R. Tolkien");
            assertThat(capturedEvent.genres()).containsExactlyInAnyOrder("Fantasy", "Adventure");
        }
    }

    @Nested
    @DisplayName("Failure Scenarios")
    class FailureScenarios {

        @Test
        @DisplayName("Should throw InvalidBookException when command is null")
        void shouldThrowExceptionWhenCommandIsNull() {
            // When / Then
            assertThatThrownBy(() -> createBookHandler.execute(null))
                    .isInstanceOf(InvalidBookException.class)
                    .hasMessageContaining("Command cannot be null");

            then(bookRepository).shouldHaveNoInteractions();
            then(genreRepository).shouldHaveNoInteractions();
            then(bookEventProducer).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("Should throw InvalidBookException when book already exists")
        void shouldThrowExceptionWhenBookAlreadyExists() {
            // Given
            var command = new CreateBookCommand(
                    "Existing Title", "Desc", 2024, "Author", "ISBN",
                    100, "Publisher", "url", Set.of("Sci-Fi")
            );

            given(bookRepository.existByTitleAndAuthorAndIsbn("Existing Title", "Author", "ISBN"))
                    .willReturn(true);

            // When / Then
            assertThatThrownBy(() -> createBookHandler.execute(command))
                    .isInstanceOf(InvalidBookException.class)
                    .hasMessageContaining("already exists");

            // Integrity checks
            then(genreRepository).shouldHaveNoInteractions();
            then(bookRepository).should(never()).save(any());
            then(bookEventProducer).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("Should throw BookRequestDomainException when genre does not exist")
        void shouldThrowExceptionWhenGenreNotFound() {
            // Given
            var command = new CreateBookCommand(
                    "Title", "Desc", 2024, "Author", "ISBN",
                    100, "Publisher", "url", Set.of("Unknown Genre")
            );

            given(bookRepository.existByTitleAndAuthorAndIsbn("Title", "Author", "ISBN"))
                    .willReturn(false);

            given(genreRepository.findByName("Unknown Genre")).willReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> createBookHandler.execute(command))
                    .isInstanceOf(BookRequestDomainException.class)
                    .hasMessageContaining("Genre not found: Unknown Genre");

            // Integrity checks
            then(bookRepository).should(never()).save(any());
            then(bookEventProducer).shouldHaveNoInteractions();
        }
    }
}