package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.mrs.catalog_service.module.book.domain.handler.GetBookHandler;
import com.mrs.catalog_service.module.book.domain.model.Genre;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetBookHandlerTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private GetBookHandler getMediaHandler;

    @Test
    @DisplayName("Should return book when it exists")
    void shouldReturnMedia_WhenMediaExists() {
        // Arrange
        UUID bookId = UUID.randomUUID();
        Book expectedMedia = Book.builder()
                .id(bookId)
                .title("Test Movie")
                .description("A test movie")
                .releaseYear(2024)
                .genres(List.of(Genre.FANTASY, Genre.SCI_FI))
                .build();

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(expectedMedia));

        // Act
        Book result = getMediaHandler.execute(bookId);

        // Assert
        assertNotNull(result);
        assertEquals(expectedMedia, result);
        assertEquals(bookId, result.getId());
        assertEquals("Test Movie", result.getTitle());

        verify(bookRepository, times(1)).findById(bookId);
    }

    @Test
    @DisplayName("Should throw BookNotFoundException when book does not exist")
    void shouldThrowException_WhenMediaDoesNotExist() {
        // Arrange
        UUID bookId = UUID.randomUUID();
        when(bookRepository.findById(bookId)).thenReturn(Optional.empty());

        // Act & Assert
        BookNotFoundException exception = assertThrows(BookNotFoundException.class, () ->
                getMediaHandler.execute(bookId)
        );

        assertTrue(exception.getMessage().contains(bookId.toString()));
        verify(bookRepository, times(1)).findById(bookId);
    }
}
