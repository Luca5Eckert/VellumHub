package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.application.dto.PageBook;
import com.mrs.catalog_service.domain.model.Genre;
import com.mrs.catalog_service.domain.model.Book;
import com.mrs.catalog_service.domain.port.BookRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetAllBooksHandlerTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private GetAllBooksHandler getAllMediaHandler;

    @Test
    @DisplayName("Should return page of book when repository has data")
    void shouldReturnPageOfMedia_WhenRepositoryHasData() {
        // Arrange
        PageBook pageMedia = new PageBook(10, 0);

        List<Book> bookList = List.of(
                Book.builder()
                        .id(UUID.randomUUID())
                        .title("Movie 1")
                        .genres(List.of(Genre.ACTION))
                        .build(),
                Book.builder()
                        .id(UUID.randomUUID())
                        .title("Series 1")
                        .genres(List.of(Genre.COMEDY))
                        .build()
        );

        Page<Book> expectedPage = new PageImpl<>(bookList);
        when(bookRepository.findAll(any(PageRequest.class))).thenReturn(expectedPage);

        // Act
        Page<Book> result = getAllMediaHandler.execute(pageMedia);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals(bookList, result.getContent());

        ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(bookRepository, times(1)).findAll(pageRequestCaptor.capture());

        PageRequest capturedPageRequest = pageRequestCaptor.getValue();
        assertEquals(0, capturedPageRequest.getPageNumber());
        assertEquals(10, capturedPageRequest.getPageSize());
    }

    @Test
    @DisplayName("Should return empty page when repository has no data")
    void shouldReturnEmptyPage_WhenRepositoryHasNoData() {
        // Arrange
        PageBook pageMedia = new PageBook(10, 0);
        Page<Book> emptyPage = new PageImpl<>(Collections.emptyList());
        when(bookRepository.findAll(any(PageRequest.class))).thenReturn(emptyPage);

        // Act
        Page<Book> result = getAllMediaHandler.execute(pageMedia);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    @DisplayName("Should create correct PageRequest with provided pagination")
    void shouldCreateCorrectPageRequest_WithProvidedPagination() {
        // Arrange
        PageBook pageMedia = new PageBook(20, 2);
        when(bookRepository.findAll(any(PageRequest.class))).thenReturn(Page.empty());

        // Act
        getAllMediaHandler.execute(pageMedia);

        // Assert
        ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(bookRepository).findAll(pageRequestCaptor.capture());

        PageRequest capturedRequest = pageRequestCaptor.getValue();
        assertEquals(2, capturedRequest.getPageNumber());
        assertEquals(20, capturedRequest.getPageSize());
    }
}
