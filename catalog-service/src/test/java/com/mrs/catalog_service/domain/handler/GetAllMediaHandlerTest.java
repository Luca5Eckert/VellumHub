package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.application.dto.PageMedia;
import com.mrs.catalog_service.domain.model.Genre;
import com.mrs.catalog_service.domain.model.Media;
import com.mrs.catalog_service.domain.model.MediaType;
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
    @DisplayName("Should return page of media with correct pagination parameters")
    void shouldReturnPageOfMedia_WithCorrectPagination() {
        // Arrange
        PageMedia pageMedia = new PageMedia(10, 0);
        
        List<Media> mediaList = List.of(
                Media.builder()
                        .id(UUID.randomUUID())
                        .title("Movie 1")
                        .releaseYear(2024)
                        .mediaType(MediaType.MOVIE)
                        .genres(List.of(Genre.ACTION))
                        .build(),
                Media.builder()
                        .id(UUID.randomUUID())
                        .title("Movie 2")
                        .releaseYear(2023)
                        .mediaType(MediaType.MOVIE)
                        .genres(List.of(Genre.THRILLER))
                        .build()
        );

        Page<Media> expectedPage = new PageImpl<>(mediaList, PageRequest.of(0, 10), mediaList.size());
        when(mediaRepository.findAll(any(PageRequest.class))).thenReturn(expectedPage);

        // Act
        Page<Media> result = getAllMediaHandler.execute(pageMedia);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals("Movie 1", result.getContent().get(0).getTitle());
        assertEquals("Movie 2", result.getContent().get(1).getTitle());

        ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(mediaRepository, times(1)).findAll(pageRequestCaptor.capture());

        PageRequest capturedPageRequest = pageRequestCaptor.getValue();
        assertEquals(0, capturedPageRequest.getPageNumber());
        assertEquals(10, capturedPageRequest.getPageSize());
    }

    @Test
    @DisplayName("Should return empty page when no media exists")
    void shouldReturnEmptyPage_WhenNoMediaExists() {
        // Arrange
        PageMedia pageMedia = new PageMedia(10, 0);
        Page<Media> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(mediaRepository.findAll(any(PageRequest.class))).thenReturn(emptyPage);

        // Act
        Page<Media> result = getAllMediaHandler.execute(pageMedia);

        // Assert
        assertNotNull(result);
        assertTrue(result.getContent().isEmpty());
        assertEquals(0, result.getTotalElements());
        verify(mediaRepository, times(1)).findAll(any(PageRequest.class));
    }

    @Test
    @DisplayName("Should use correct page size and page number from PageMedia")
    void shouldUseCorrectPaginationFromPageMedia() {
        // Arrange
        PageMedia pageMedia = new PageMedia(20, 2);
        Page<Media> mockPage = new PageImpl<>(List.of(), PageRequest.of(2, 20), 0);
        when(mediaRepository.findAll(any(PageRequest.class))).thenReturn(mockPage);

        // Act
        getAllMediaHandler.execute(pageMedia);

        // Assert
        ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(mediaRepository, times(1)).findAll(pageRequestCaptor.capture());

        PageRequest capturedPageRequest = pageRequestCaptor.getValue();
        assertEquals(2, capturedPageRequest.getPageNumber());
        assertEquals(20, capturedPageRequest.getPageSize());
    }
}
