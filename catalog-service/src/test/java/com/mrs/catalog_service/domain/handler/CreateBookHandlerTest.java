package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.domain.event.CreateBookEvent;
import com.mrs.catalog_service.domain.exception.InvalidBookException;
import com.mrs.catalog_service.domain.model.Genre;
import com.mrs.catalog_service.domain.model.Book;
import com.mrs.catalog_service.domain.port.EventProducer;
import com.mrs.catalog_service.domain.port.BookRepository;
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
    private EventProducer<String, CreateBookEvent> eventProducer;

    @InjectMocks
    private CreateBookHandler createMediaHandler;

    @Test
    @DisplayName("Should save book and send event when book is valid")
    void shouldSaveMediaAndSendEvent_WhenMediaIsValid() {
        // Arrange
        UUID bookId = UUID.randomUUID();
        List<Genre> genres = List.of(Genre.ACTION, Genre.COMEDY);

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
        verify(eventProducer, times(1)).send(
                eq("create-book"),
                eq(bookId.toString()),
                eventCaptor.capture()
        );

        CreateBookEvent capturedEvent = eventCaptor.getValue();
        assertEquals(bookId, capturedEvent.bookId());
        assertEquals(genres.stream(), capturedEvent.genres());
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
        verifyNoInteractions(eventProducer);
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
        verify(eventProducer, times(1)).send(
                eq("create-book"),
                eq(bookId.toString()),
                eventCaptor.capture()
        );

        CreateBookEvent capturedEvent = eventCaptor.getValue();
        assertEquals(bookId, capturedEvent.bookId());
        assertTrue(capturedEvent.genres().isEmpty());
    }
}
