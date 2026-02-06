package com.mrs.catalog_service.service;

import com.mrs.catalog_service.application.dto.CreateMediaRequest;
import com.mrs.catalog_service.application.dto.GetMediaResponse;
import com.mrs.catalog_service.application.dto.PageMedia;
import com.mrs.catalog_service.application.dto.UpdateMediaRequest;
import com.mrs.catalog_service.application.mapper.MediaMapper;
import com.mrs.catalog_service.domain.handler.*;
import com.mrs.catalog_service.domain.model.Genre;
import com.mrs.catalog_service.domain.model.Media;
import com.mrs.catalog_service.domain.service.MediaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MediaService.
 * Tests cover all service methods with mocked handlers.
 * Follows the pattern: condition_expectedBehavior.
 */
@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock
    private CreateMediaHandler createMediaHandler;

    @Mock
    private DeleteMediaHandler deleteMediaHandler;

    @Mock
    private GetMediaHandler getMediaHandler;

    @Mock
    private GetAllMediaHandler getAllMediaHandler;

    @Mock
    private UpdateMediaHandler updateMediaHandler;

    @Mock
    private MediaMapper mediaMapper;

    @InjectMocks
    private MediaService mediaService;

    @Nested
    @DisplayName("create() method tests")
    class CreateTests {

        @Test
        @DisplayName("whenValidRequest_shouldCreateMedia")
        void whenValidRequest_shouldCreateMedia() {
            // Arrange
            CreateMediaRequest request = new CreateMediaRequest(
                    "The Matrix",
                    "A computer hacker learns about the true nature of reality",
                    1999,
                    MediaType.BOOK,
                    "https://example.com/matrix.jpg",
                    List.of(Genre.ACTION, Genre.THRILLER)
            );

            // Act
            mediaService.create(request);

            // Assert
            ArgumentCaptor<Media> mediaCaptor = ArgumentCaptor.forClass(Media.class);
            verify(createMediaHandler, times(1)).handler(mediaCaptor.capture());

            Media capturedMedia = mediaCaptor.getValue();
            assertThat(capturedMedia.getTitle()).isEqualTo("The Matrix");
            assertThat(capturedMedia.getDescription()).isEqualTo("A computer hacker learns about the true nature of reality");
            assertThat(capturedMedia.getReleaseYear()).isEqualTo(1999);
            assertThat(capturedMedia.getMediaType()).isEqualTo(MediaType.BOOK);
            assertThat(capturedMedia.getGenres()).containsExactlyInAnyOrder(Genre.ACTION, Genre.THRILLER);
        }

        @Test
        @DisplayName("whenSeriesType_shouldCreateSeries")
        void whenSeriesType_shouldCreateSeries() {
            // Arrange
            CreateMediaRequest request = new CreateMediaRequest(
                    "Breaking Bad",
                    "A chemistry teacher turns to a life of crime",
                    2008,
                    MediaType.BOOK,
                    "https://example.com/bb.jpg",
                    List.of(Genre.THRILLER, Genre.ACTION)
            );

            // Act
            mediaService.create(request);

            // Assert
            ArgumentCaptor<Media> mediaCaptor = ArgumentCaptor.forClass(Media.class);
            verify(createMediaHandler).handler(mediaCaptor.capture());

            Media capturedMedia = mediaCaptor.getValue();
            assertThat(capturedMedia.getMediaType()).isEqualTo(MediaType.BOOK);
        }
    }

    @Nested
    @DisplayName("delete() method tests")
    class DeleteTests {

        @Test
        @DisplayName("whenValidMediaId_shouldDeleteMedia")
        void whenValidMediaId_shouldDeleteMedia() {
            // Arrange
            UUID mediaId = UUID.randomUUID();

            // Act
            mediaService.delete(mediaId);

            // Assert
            verify(deleteMediaHandler, times(1)).execute(mediaId);
        }
    }

    @Nested
    @DisplayName("get() method tests")
    class GetTests {

        @Test
        @DisplayName("whenValidMediaId_shouldReturnMediaResponse")
        void whenValidMediaId_shouldReturnMediaResponse() {
            // Arrange
            UUID mediaId = UUID.randomUUID();
            Instant now = Instant.now();

            Media media = Media.builder()
                    .id(mediaId)
                    .title("The Matrix")
                    .description("A computer hacker learns about the true nature of reality")
                    .releaseYear(1999)
                    .mediaType(MediaType.BOOK)
                    .coverUrl("https://example.com/matrix.jpg")
                    .genres(List.of(Genre.ACTION, Genre.THRILLER))
                    .build();

            GetMediaResponse expectedResponse = new GetMediaResponse(
                    mediaId,
                    "The Matrix",
                    "A computer hacker learns about the true nature of reality",
                    1999,
                    MediaType.BOOK,
                    "https://example.com/matrix.jpg",
                    List.of(Genre.ACTION, Genre.THRILLER),
                    now,
                    now
            );

            when(getMediaHandler.execute(mediaId)).thenReturn(media);
            when(mediaMapper.toGetResponse(media)).thenReturn(expectedResponse);

            // Act
            GetMediaResponse result = mediaService.get(mediaId);

            // Assert
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(result.id()).isEqualTo(mediaId);
            assertThat(result.title()).isEqualTo("The Matrix");

            verify(getMediaHandler, times(1)).execute(mediaId);
            verify(mediaMapper, times(1)).toGetResponse(media);
        }
    }

    @Nested
    @DisplayName("getAll() method tests")
    class GetAllTests {

        @Test
        @DisplayName("whenMediaExists_shouldReturnMediaList")
        void whenMediaExists_shouldReturnMediaList() {
            // Arrange
            int pageNumber = 0;
            int pageSize = 10;
            Instant now = Instant.now();

            UUID mediaId1 = UUID.randomUUID();
            UUID mediaId2 = UUID.randomUUID();

            Media media1 = Media.builder()
                    .id(mediaId1)
                    .title("The Matrix")
                    .mediaType(MediaType.BOOK)
                    .genres(List.of(Genre.ACTION))
                    .build();

            Media media2 = Media.builder()
                    .id(mediaId2)
                    .title("Breaking Bad")
                    .mediaType(MediaType.BOOK)
                    .genres(List.of(Genre.HORROR))
                    .build();

            Page<Media> mediaPage = new PageImpl<>(List.of(media1, media2));

            GetMediaResponse response1 = new GetMediaResponse(mediaId1, "The Matrix", "Desc 1",
                    1999, MediaType.BOOK, "url1", List.of(Genre.ACTION), now, now);
            GetMediaResponse response2 = new GetMediaResponse(mediaId2, "Breaking Bad", "Desc 2",
                    2008, MediaType.BOOK, "url2", List.of(Genre.HORROR), now, now);

            when(getAllMediaHandler.execute(any(PageMedia.class))).thenReturn(mediaPage);
            when(mediaMapper.toGetResponse(media1)).thenReturn(response1);
            when(mediaMapper.toGetResponse(media2)).thenReturn(response2);

            // Act
            List<GetMediaResponse> result = mediaService.getAll(pageNumber, pageSize);

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).title()).isEqualTo("The Matrix");
            assertThat(result.get(1).title()).isEqualTo("Breaking Bad");

            ArgumentCaptor<PageMedia> pageCaptor = ArgumentCaptor.forClass(PageMedia.class);
            verify(getAllMediaHandler, times(1)).execute(pageCaptor.capture());

            PageMedia capturedPage = pageCaptor.getValue();
            assertThat(capturedPage.pageNumber()).isEqualTo(pageNumber);
            assertThat(capturedPage.pageSize()).isEqualTo(pageSize);
        }

        @Test
        @DisplayName("whenNoMedia_shouldReturnEmptyList")
        void whenNoMedia_shouldReturnEmptyList() {
            // Arrange
            int pageNumber = 0;
            int pageSize = 10;

            Page<Media> emptyPage = new PageImpl<>(List.of());
            when(getAllMediaHandler.execute(any(PageMedia.class))).thenReturn(emptyPage);

            // Act
            List<GetMediaResponse> result = mediaService.getAll(pageNumber, pageSize);

            // Assert
            assertThat(result).isEmpty();
            verify(getAllMediaHandler, times(1)).execute(any(PageMedia.class));
            verify(mediaMapper, never()).toGetResponse(any(Media.class));
        }
    }

    @Nested
    @DisplayName("update() method tests")
    class UpdateTests {

        @Test
        @DisplayName("whenValidRequest_shouldUpdateMedia")
        void whenValidRequest_shouldUpdateMedia() {
            // Arrange
            UUID mediaId = UUID.randomUUID();
            UpdateMediaRequest request = new UpdateMediaRequest(
                    "Updated Title",
                    "Updated Description",
                    2000,
                    "https://example.com/updated.jpg",
                    List.of(Genre.COMEDY, Genre.HORROR)
            );

            // Act
            mediaService.update(mediaId, request);

            // Assert
            verify(updateMediaHandler, times(1)).execute(mediaId, request);
        }
    }
}
