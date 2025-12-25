package com.mrs.catalog_service.service;

import com.mrs.catalog_service.dto.CreateMediaRequest;
import com.mrs.catalog_service.dto.GetMediaResponse;
import com.mrs.catalog_service.dto.PageMedia;
import com.mrs.catalog_service.handler.CreateMediaHandler;
import com.mrs.catalog_service.handler.DeleteMediaHandler;
import com.mrs.catalog_service.handler.GetAllMediaHandler;
import com.mrs.catalog_service.handler.GetMediaHandler;
import com.mrs.catalog_service.mapper.MediaMapper;
import com.mrs.catalog_service.model.Genre;
import com.mrs.catalog_service.model.Media;
import com.mrs.catalog_service.model.MediaType;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MediaService Tests")
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
    private MediaMapper mediaMapper;

    @InjectMocks
    private MediaService mediaService;

    @Nested
    @DisplayName("create() method")
    class CreateTests {

        @Test
        @DisplayName("Should create media with correct data")
        void create_WithValidRequest_CreatesMedia() {
            CreateMediaRequest request = new CreateMediaRequest(
                    "Inception",
                    "A mind-bending thriller",
                    2010,
                    MediaType.MOVIE,
                    "http://example.com/cover.jpg",
                    List.of(Genre.ACTION, Genre.THRILLER)
            );

            doNothing().when(createMediaHandler).handler(any(Media.class));

            mediaService.create(request);

            ArgumentCaptor<Media> mediaCaptor = ArgumentCaptor.forClass(Media.class);
            verify(createMediaHandler, times(1)).handler(mediaCaptor.capture());

            Media capturedMedia = mediaCaptor.getValue();
            assertThat(capturedMedia.getTitle()).isEqualTo("Inception");
            assertThat(capturedMedia.getDescription()).isEqualTo("A mind-bending thriller");
            assertThat(capturedMedia.getReleaseYear()).isEqualTo(2010);
            assertThat(capturedMedia.getMediaType()).isEqualTo(MediaType.MOVIE);
            assertThat(capturedMedia.getGenres()).containsExactlyInAnyOrder(Genre.ACTION, Genre.THRILLER);
        }

        @Test
        @DisplayName("Should set timestamps when creating media")
        void create_SetsTimestamps() {
            CreateMediaRequest request = new CreateMediaRequest(
                    "Test Movie",
                    "Description",
                    2024,
                    MediaType.MOVIE,
                    null,
                    List.of(Genre.ACTION)
            );

            doNothing().when(createMediaHandler).handler(any(Media.class));

            mediaService.create(request);

            ArgumentCaptor<Media> mediaCaptor = ArgumentCaptor.forClass(Media.class);
            verify(createMediaHandler).handler(mediaCaptor.capture());

            Media capturedMedia = mediaCaptor.getValue();
            assertThat(capturedMedia.getCreateAt()).isNotNull();
            assertThat(capturedMedia.getUpdateAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("delete() method")
    class DeleteTests {

        @Test
        @DisplayName("Should delete media by ID")
        void delete_WithValidId_DeletesMedia() {
            UUID mediaId = UUID.randomUUID();
            doNothing().when(deleteMediaHandler).execute(mediaId);

            mediaService.delete(mediaId);

            verify(deleteMediaHandler, times(1)).execute(mediaId);
        }

        @Test
        @DisplayName("Should propagate exception when media not found")
        void delete_WhenMediaNotFound_PropagatesException() {
            UUID mediaId = UUID.randomUUID();
            doThrow(new IllegalArgumentException("Media not exist"))
                    .when(deleteMediaHandler).execute(mediaId);

            assertThatThrownBy(() -> mediaService.delete(mediaId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Media not exist");
        }
    }

    @Nested
    @DisplayName("get() method")
    class GetTests {

        @Test
        @DisplayName("Should return media response when media exists")
        void get_WhenMediaExists_ReturnsMediaResponse() {
            UUID mediaId = UUID.randomUUID();
            Media media = new Media.Builder()
                    .title("Inception")
                    .genres(List.of(Genre.ACTION))
                    .build();
            media.setId(mediaId);

            GetMediaResponse expectedResponse = new GetMediaResponse(
                    mediaId, "Inception", "Description", 2010, MediaType.MOVIE, List.of(Genre.ACTION)
            );

            when(getMediaHandler.execute(mediaId)).thenReturn(media);
            when(mediaMapper.toGetResponse(media)).thenReturn(expectedResponse);

            GetMediaResponse result = mediaService.get(mediaId);

            assertThat(result).isEqualTo(expectedResponse);
            verify(getMediaHandler, times(1)).execute(mediaId);
            verify(mediaMapper, times(1)).toGetResponse(media);
        }

        @Test
        @DisplayName("Should propagate exception when media not found")
        void get_WhenMediaNotFound_PropagatesException() {
            UUID mediaId = UUID.randomUUID();
            when(getMediaHandler.execute(mediaId))
                    .thenThrow(new RuntimeException("Media not found"));

            assertThatThrownBy(() -> mediaService.get(mediaId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Media not found");
        }
    }

    @Nested
    @DisplayName("getAll() method")
    class GetAllTests {

        @Test
        @DisplayName("Should return list of media responses")
        void getAll_ReturnsMediaResponses() {
            UUID mediaId1 = UUID.randomUUID();
            UUID mediaId2 = UUID.randomUUID();

            Media media1 = new Media.Builder().title("Movie 1").genres(List.of(Genre.ACTION)).build();
            media1.setId(mediaId1);

            Media media2 = new Media.Builder().title("Movie 2").genres(List.of(Genre.HORROR)).build();
            media2.setId(mediaId2);

            Page<Media> mediaPage = new PageImpl<>(List.of(media1, media2));

            GetMediaResponse response1 = new GetMediaResponse(mediaId1, "Movie 1", null, 0, null, List.of(Genre.ACTION));
            GetMediaResponse response2 = new GetMediaResponse(mediaId2, "Movie 2", null, 0, null, List.of(Genre.HORROR));

            when(getAllMediaHandler.execute(any(PageMedia.class))).thenReturn(mediaPage);
            when(mediaMapper.toGetResponse(media1)).thenReturn(response1);
            when(mediaMapper.toGetResponse(media2)).thenReturn(response2);

            List<GetMediaResponse> result = mediaService.getAll(0, 10);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(response1, response2);
        }

        @Test
        @DisplayName("Should return empty list when no media exist")
        void getAll_WhenNoMedia_ReturnsEmptyList() {
            Page<Media> emptyPage = Page.empty();
            when(getAllMediaHandler.execute(any(PageMedia.class))).thenReturn(emptyPage);

            List<GetMediaResponse> result = mediaService.getAll(0, 10);

            assertThat(result).isEmpty();
        }
    }
}
