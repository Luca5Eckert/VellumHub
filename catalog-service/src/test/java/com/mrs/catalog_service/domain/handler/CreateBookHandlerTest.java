package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.module.book.domain.event.CreateBookEvent;
import com.mrs.catalog_service.module.book.domain.exception.InvalidBookException;
import com.mrs.catalog_service.module.book.domain.handler.CreateBookHandler;
import com.mrs.catalog_service.module.book.domain.model.Genre;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.port.BookEventProducer;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateBookHandlerTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookEventProducer<String, CreateBookEvent> bookEventProducer;

    @InjectMocks
    private CreateBookHandler createMediaHandler;

    @Test
    @DisplayName("Should save book and send event when book is valid")
    void shouldSaveMediaAndSendEvent_WhenMediaIsValid() {
        // Arrange
        UUID bookId = UUID.randomUUID();
        List<Genre> genres = List.of(Genre.FANTASY, Genre.SCI_FI);

        Book book = Book.builder()
                .id(bookId)
                .title("Test Movie")
                .description("A test movie description")
                .releaseYear(2024)
                .genres(genres)
                .build();

        // Act
        createMediaHandler.handler(book);

        // Assert
        verify(bookRepository, times(1)).save(book);

        ArgumentCaptor<CreateBookEvent> eventCaptor = ArgumentCaptor.forClass(CreateBookEvent.class);
        verify(bookEventProducer, times(1)).send(
                eq("created-book"),
                eq(bookId.toString()),
                eventCaptor.capture()
        );

        CreateBookEvent capturedEvent = eventCaptor.getValue();
        assertEquals(bookId, capturedEvent.bookId());
        assertEquals(genres, capturedEvent.genres());
    }

    @Test
    @DisplayName("Should throw InvalidBookException when book is null")
    void shouldThrowException_WhenMediaIsNull() {
        // Act & Assert
        InvalidBookException exception = assertThrows(InvalidBookException.class, () ->
                createMediaHandler.handler(null)
        );

        assertEquals("Book cannot be null", exception.getMessage());
        verifyNoInteractions(bookRepository);
        verifyNoInteractions(bookEventProducer);
    }

    @Test
    @DisplayName("Should save book with empty genres and send event")
    void shouldSaveMediaWithEmptyGenres_AndSendEvent() {
        // Arrange
        UUID bookId = UUID.randomUUID();

        Book book = Book.builder()
                .id(bookId)
                .title("Test Movie")
                .description("A test movie description")
                .releaseYear(2024)
                .genres(List.of())
                .build();

        // Act
        createMediaHandler.handler(book);

        // Assert
        verify(bookRepository, times(1)).save(book);

        ArgumentCaptor<CreateBookEvent> eventCaptor = ArgumentCaptor.forClass(CreateBookEvent.class);
        verify(bookEventProducer, times(1)).send(
                eq("created-book"),
                eq(bookId.toString()),
                eventCaptor.capture()
        );

        CreateBookEvent capturedEvent = eventCaptor.getValue();
        assertEquals(bookId, capturedEvent.bookId());
        assertTrue(capturedEvent.genres().isEmpty());
    }
}
