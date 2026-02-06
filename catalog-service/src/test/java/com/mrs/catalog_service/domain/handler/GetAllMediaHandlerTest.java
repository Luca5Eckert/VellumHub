package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.application.dto.PageMedia;
import com.mrs.catalog_service.domain.model.Genre;
import com.mrs.catalog_service.domain.model.Book;
import com.mrs.catalog_service.domain.port.MediaRepository;
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
class GetAllMediaHandlerTest {

    @Mock
    private MediaRepository mediaRepository;

    @InjectMocks
    private GetAllMediaHandler getAllMediaHandler;

    @Test
    @DisplayName("Should return page of media when repository has data")
    void shouldReturnPageOfMedia_WhenRepositoryHasData() {
        // Arrange
        PageMedia pageMedia = new PageMedia(10, 0);

        List<Book> mediaList = List.of(
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

        Page<Book> expectedPage = new PageImpl<>(mediaList);
        when(mediaRepository.findAll(any(PageRequest.class))).thenReturn(expectedPage);

        // Act
        Page<Book> result = getAllMediaHandler.execute(pageMedia);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals(mediaList, result.getContent());

        ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(mediaRepository, times(1)).findAll(pageRequestCaptor.capture());

        PageRequest capturedPageRequest = pageRequestCaptor.getValue();
        assertEquals(0, capturedPageRequest.getPageNumber());
        assertEquals(10, capturedPageRequest.getPageSize());
    }

    @Test
    @DisplayName("Should return empty page when repository has no data")
    void shouldReturnEmptyPage_WhenRepositoryHasNoData() {
        // Arrange
        PageMedia pageMedia = new PageMedia(10, 0);
        Page<Book> emptyPage = new PageImpl<>(Collections.emptyList());
        when(mediaRepository.findAll(any(PageRequest.class))).thenReturn(emptyPage);

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
        PageMedia pageMedia = new PageMedia(20, 2);
        when(mediaRepository.findAll(any(PageRequest.class))).thenReturn(Page.empty());

        // Act
        getAllMediaHandler.execute(pageMedia);

        // Assert
        ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(mediaRepository).findAll(pageRequestCaptor.capture());

        PageRequest capturedRequest = pageRequestCaptor.getValue();
        assertEquals(2, capturedRequest.getPageNumber());
        assertEquals(20, capturedRequest.getPageSize());
    }
}
