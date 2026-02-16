package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.module.book.application.dto.UpdateBookRequest;
import com.mrs.catalog_service.module.book.domain.event.UpdateBookEvent;
import com.mrs.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.mrs.catalog_service.module.book.domain.handler.UpdateBookHandler;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateBookHandlerTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookEventProducer<String, UpdateBookEvent> bookEventProducer;

    @InjectMocks
    private UpdateBookHandler updateMediaHandler;

    @Test
    @DisplayName("Should update book and send event when genres are provided")
    void shouldUpdateMediaAndSendEvent_WhenGenresArePresent() {
        // Arrange
        UUID bookId = UUID.randomUUID();
        List<Genre> newGenres = List.of(Genre.FANTASY, Genre.SCI_FI);

        UpdateBookRequest request = new UpdateBookRequest(
                "New Title",
                "New Desc",
                2024,
                "http://url.com",
                "Author Name",
                "978-0-7653-0000-0",
                300,
                "Publisher Name",
                newGenres
        );


        Book existingMedia = Book.builder()
                .id(bookId)
                .title("Old Title")
                .genres(List.of(Genre.SCI_FI))
                .build();

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(existingMedia));

        // Act
        updateMediaHandler.execute(bookId, request);

        // Assert
        ArgumentCaptor<Book> mediaCaptor = ArgumentCaptor.forClass(Book.class);
        verify(bookRepository).save(mediaCaptor.capture());

        Book savedMedia = mediaCaptor.getValue();
        assertEquals("New Title", savedMedia.getTitle());

        ArgumentCaptor<UpdateBookEvent> eventCaptor = ArgumentCaptor.forClass(UpdateBookEvent.class);
        verify(bookEventProducer, times(1))
                .send(eq("updated-book"), eq(bookId.toString()), eventCaptor.capture());

        assertEquals(bookId, eventCaptor.getValue().bookId());
        assertEquals(newGenres, eventCaptor.getValue().genres());
    }

    @Test
    @DisplayName("Should update book but NOT send event when genres are null")
    void shouldUpdateMediaButNotSendEvent_WhenGenresAreNull() {
        // Arrange
        UUID bookId = UUID.randomUUID();

        UpdateBookRequest request = new UpdateBookRequest(
                "New Title",
                "New Desc",
                2024,
                "http://url.com",
                "Author Name",
                "978-0-7653-0000-0",
                300,
                "Publisher Name",
                null
        );

        Book existingMedia = Book.builder()
                .id(bookId)
                .title("Old Title")
                .genres(List.of(Genre.SCI_FI))
                .build();

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(existingMedia));

        // Act
        updateMediaHandler.execute(bookId, request);

        // Assert
        verify(bookRepository).save(any(Book.class));

        verify(bookEventProducer, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw BookNotFoundException when book does not exist")
    void shouldThrowException_WhenMediaNotFound() {
        // Arrange
        UUID bookId = UUID.randomUUID();
        UpdateBookRequest request = new UpdateBookRequest("T", "D", 2022, "U", "A", "978-0-7653-0000-0", 300, "P", List.of(Genre.THRILLER_MYSTERY));

        when(bookRepository.findById(bookId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BookNotFoundException.class, () ->
                updateMediaHandler.execute(bookId, request)
        );

        verify(bookRepository, never()).save(any());
        verify(bookEventProducer, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw NullPointerException when request is null")
    void shouldThrowException_WhenRequestIsNull() {
        // Arrange
        UUID bookId = UUID.randomUUID();

        // Act & Assert
        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                updateMediaHandler.execute(bookId, null)
        );

        assertEquals("UpdateBookRequest must not be null", exception.getMessage());
        verifyNoInteractions(bookRepository);
        verifyNoInteractions(bookEventProducer);
    }

}