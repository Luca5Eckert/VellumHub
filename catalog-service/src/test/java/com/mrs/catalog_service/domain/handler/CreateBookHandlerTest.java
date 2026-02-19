package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.module.book.domain.event.CreateBookEvent;
import com.mrs.catalog_service.module.book.domain.exception.InvalidBookException;
import com.mrs.catalog_service.module.book.domain.handler.CreateBookHandler;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.model.Genre;
import com.mrs.catalog_service.module.book.domain.port.BookEventProducer;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateBookHandler Unit Tests")
class CreateBookHandlerTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookEventProducer<String, CreateBookEvent> bookEventProducer;

    @InjectMocks
    private CreateBookHandler createBookHandler;

    @Nested
    @DisplayName("Success Scenarios")
    class SuccessScenarios {

        @Test
        @DisplayName("Should save book and produce event when book is valid")
        void shouldCreateBookSuccessfully() {
            // Given
            UUID bookId = UUID.randomUUID();
            List<Genre> genres = List.of(Genre.FANTASY, Genre.HORROR);

            Book book = mock(Book.class);
            given(book.getId()).willReturn(bookId);
            given(book.getTitle()).willReturn("The Hobbit");
            given(book.getAuthor()).willReturn("J.R.R. Tolkien");
            given(book.getIsbn()).willReturn("123456789");
            given(book.getGenres()).willReturn(genres);

            given(bookRepository.existByTitleAndAuthorAndIsbn(any(), any(), any())).willReturn(false);

            // When
            createBookHandler.handler(book);

            // Then
            then(bookRepository).should().save(book);

            // Verifying the event payload
            ArgumentCaptor<CreateBookEvent> eventCaptor = ArgumentCaptor.forClass(CreateBookEvent.class);
            then(bookEventProducer).should().send(
                    eq("created-book"),
                    eq(bookId.toString()),
                    eventCaptor.capture()
            );

            CreateBookEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.bookId()).isEqualTo(bookId);
            assertThat(capturedEvent.genres()).isEqualTo(genres);
        }
    }

    @Nested
    @DisplayName("Failure Scenarios")
    class FailureScenarios {

        @Test
        @DisplayName("Should throw InvalidBookException when book is null")
        void shouldThrowExceptionWhenBookIsNull() {
            assertThatThrownBy(() -> createBookHandler.handler(null))
                    .isInstanceOf(InvalidBookException.class);

            then(bookRepository).shouldHaveNoInteractions();
            then(bookEventProducer).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("Should throw InvalidBookException when book already exists")
        void shouldThrowExceptionWhenBookAlreadyExists() {
            // Given
            Book book = mock(Book.class);
            given(book.getTitle()).willReturn("Existing Title");
            given(book.getAuthor()).willReturn("Author");
            given(book.getIsbn()).willReturn("ISBN");

            given(bookRepository.existByTitleAndAuthorAndIsbn("Existing Title", "Author", "ISBN"))
                    .willReturn(true);

            // When / Then
            assertThatThrownBy(() -> createBookHandler.handler(book))
                    .isInstanceOf(InvalidBookException.class)
                    .hasMessageContaining("already exists");

            // Integrity check: Ensure no save or event happened
            then(bookRepository).should(never()).save(any());
            then(bookEventProducer).shouldHaveNoInteractions();
        }
    }
}