package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.domain.event.DeleteBookEvent;
import com.mrs.catalog_service.domain.exception.BookNotExistException;
import com.mrs.catalog_service.domain.port.BookRepository;
import com.mrs.catalog_service.domain.port.EventProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteBookHandlerTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private EventProducer<String, DeleteBookEvent> eventProducer;

    @InjectMocks
    private DeleteBookHandler deleteMediaHandler;

    @Test
    @DisplayName("Should delete book and send event when book exists")
    void shouldDeleteMediaAndSendEvent_WhenMediaExists() {
        // Arrange
        UUID bookId = UUID.randomUUID();
        when(bookRepository.existsById(bookId)).thenReturn(true);

        // Act
        deleteMediaHandler.execute(bookId);

        // Assert
        verify(bookRepository, times(1)).existsById(bookId);
        verify(bookRepository, times(1)).deleteById(bookId);

        ArgumentCaptor<DeleteBookEvent> eventCaptor = ArgumentCaptor.forClass(DeleteBookEvent.class);
        verify(eventProducer, times(1)).send(
                eq("delete-book"),
                eq(bookId.toString()),
                eventCaptor.capture()
        );

        DeleteBookEvent capturedEvent = eventCaptor.getValue();
        assertEquals(bookId, capturedEvent.bookId());
    }

    @Test
    @DisplayName("Should throw BookNotExistException when book does not exist")
    void shouldThrowException_WhenMediaDoesNotExist() {
        // Arrange
        UUID bookId = UUID.randomUUID();
        when(bookRepository.existsById(bookId)).thenReturn(false);

        // Act & Assert
        BookNotExistException exception = assertThrows(BookNotExistException.class, () ->
                deleteMediaHandler.execute(bookId)
        );

        assertTrue(exception.getMessage().contains(bookId.toString()));
        verify(bookRepository, times(1)).existsById(bookId);
        verify(bookRepository, never()).deleteById(any());
        verifyNoInteractions(eventProducer);
    }
}
