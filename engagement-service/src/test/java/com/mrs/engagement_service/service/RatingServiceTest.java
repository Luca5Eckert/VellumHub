package com.mrs.engagement_service.service;

import com.mrs.engagement_service.application.dto.GetMediaStatusResponse;
import com.mrs.engagement_service.application.dto.RatingCreateRequest;
import com.mrs.engagement_service.application.dto.RatingGetResponse;
import com.mrs.engagement_service.application.dto.filter.RatingFilter;
import com.mrs.engagement_service.domain.handler.CreateRatingHandler;
import com.mrs.engagement_service.domain.handler.GetMediaStatsHandler;
import com.mrs.engagement_service.domain.handler.GetUserRatingHandler;
import com.mrs.engagement_service.domain.model.EngagementStats;
import com.mrs.engagement_service.domain.model.Rating;
import com.mrs.engagement_service.domain.port.RatingMapper;
import com.mrs.engagement_service.domain.service.RatingService;
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

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RatingService.
 * Tests cover all service methods with mocked handlers.
 * Follows the pattern: condition_expectedBehavior.
 */
@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock
    private CreateRatingHandler createRatingHandler;

    @Mock
    private GetUserRatingHandler getUserRatingHandler;

    @Mock
    private GetMediaStatsHandler getMediaStatsHandler;

    @Mock
    private RatingMapper ratingMapper;

    @InjectMocks
    private RatingService ratingService;

    @Nested
    @DisplayName("create() method tests")
    class CreateTests {

        @Test
        @DisplayName("whenValidRatingRequest_shouldCreateRating")
        void whenValidRatingRequest_shouldCreateRating() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();

            RatingCreateRequest request = new RatingCreateRequest(
                    userId,
                    mediaId,
                    4,
                    "Great movie!"
            );

            // Act
            ratingService.create(request);

            // Assert
            ArgumentCaptor<Rating> ratingCaptor = ArgumentCaptor.forClass(Rating.class);
            verify(createRatingHandler, times(1)).handler(ratingCaptor.capture());

            Rating capturedRating = ratingCaptor.getValue();
            assertThat(capturedRating.getUserId()).isEqualTo(userId);
            assertThat(capturedRating.getMediaId()).isEqualTo(mediaId);
            assertThat(capturedRating.getStars()).isEqualTo(4);
            assertThat(capturedRating.getReview()).isEqualTo("Great movie!");
            assertThat(capturedRating.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("whenValidFiveStarRating_shouldCreateRatingWithValue")
        void whenValidFiveStarRating_shouldCreateRatingWithValue() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();

            RatingCreateRequest request = new RatingCreateRequest(
                    userId,
                    mediaId,
                    5,
                    "Perfect!"
            );

            // Act
            ratingService.create(request);

            // Assert
            ArgumentCaptor<Rating> ratingCaptor = ArgumentCaptor.forClass(Rating.class);
            verify(createRatingHandler).handler(ratingCaptor.capture());

            Rating capturedRating = ratingCaptor.getValue();
            assertThat(capturedRating.getStars()).isEqualTo(5);
            assertThat(capturedRating.getReview()).isEqualTo("Perfect!");
        }

        @Test
        @DisplayName("whenZeroStarRating_shouldCreateRating")
        void whenZeroStarRating_shouldCreateRating() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();

            RatingCreateRequest request = new RatingCreateRequest(
                    userId,
                    mediaId,
                    0,
                    "Terrible"
            );

            // Act
            ratingService.create(request);

            // Assert
            ArgumentCaptor<Rating> ratingCaptor = ArgumentCaptor.forClass(Rating.class);
            verify(createRatingHandler).handler(ratingCaptor.capture());

            Rating capturedRating = ratingCaptor.getValue();
            assertThat(capturedRating.getStars()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("findAllOfUser() method tests")
    class FindAllOfUserTests {

        @Test
        @DisplayName("whenUserHasRatings_shouldReturnRatingList")
        void whenUserHasRatings_shouldReturnRatingList() {
            // Arrange
            UUID userId = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();

            Rating rating1 = new Rating(
                    userId,
                    UUID.randomUUID(),
                    4,
                    "Good",
                    now
            );

            Rating rating2 = new Rating(
                    userId,
                    UUID.randomUUID(),
                    5,
                    "Excellent",
                    now
            );

            Page<Rating> ratingsPage = new PageImpl<>(List.of(rating1, rating2));

            RatingGetResponse response1 = new RatingGetResponse(
                    1L, userId, rating1.getMediaId(), 4, "Good", now
            );
            RatingGetResponse response2 = new RatingGetResponse(
                    2L, userId, rating2.getMediaId(), 5, "Excellent", now
            );

            when(getUserRatingHandler.execute(any(RatingFilter.class), eq(userId), anyInt(), anyInt()))
                    .thenReturn(ratingsPage);
            when(ratingMapper.toGetResponse(rating1)).thenReturn(response1);
            when(ratingMapper.toGetResponse(rating2)).thenReturn(response2);

            // Act
            List<RatingGetResponse> result = ratingService.findAllOfUser(
                    userId, null, null, null, null, 0, 10
            );

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).stars()).isEqualTo(4);
            assertThat(result.get(1).stars()).isEqualTo(5);

            verify(getUserRatingHandler, times(1))
                    .execute(any(RatingFilter.class), eq(userId), eq(10), eq(0));
        }

        @Test
        @DisplayName("whenMinStarsFilterProvided_shouldCreateFilterWithMinStars")
        void whenMinStarsFilterProvided_shouldCreateFilterWithMinStars() {
            // Arrange
            UUID userId = UUID.randomUUID();
            Integer minStars = 3;

            Page<Rating> emptyPage = new PageImpl<>(List.of());
            when(getUserRatingHandler.execute(any(RatingFilter.class), eq(userId), anyInt(), anyInt()))
                    .thenReturn(emptyPage);

            // Act
            ratingService.findAllOfUser(userId, minStars, null, null, null, 0, 10);

            // Assert
            ArgumentCaptor<RatingFilter> filterCaptor = ArgumentCaptor.forClass(RatingFilter.class);
            verify(getUserRatingHandler).execute(filterCaptor.capture(), eq(userId), anyInt(), anyInt());

            RatingFilter capturedFilter = filterCaptor.getValue();
            assertThat(capturedFilter.minStars()).isEqualTo(3);
        }

        @Test
        @DisplayName("whenDateFiltersProvided_shouldCreateFilterWithDates")
        void whenDateFiltersProvided_shouldCreateFilterWithDates() {
            // Arrange
            UUID userId = UUID.randomUUID();
            OffsetDateTime fromDate = OffsetDateTime.now().minusDays(7);
            OffsetDateTime toDate = OffsetDateTime.now();

            Page<Rating> emptyPage = new PageImpl<>(List.of());
            when(getUserRatingHandler.execute(any(RatingFilter.class), eq(userId), anyInt(), anyInt()))
                    .thenReturn(emptyPage);

            // Act
            ratingService.findAllOfUser(userId, null, null, fromDate, toDate, 0, 10);

            // Assert
            ArgumentCaptor<RatingFilter> filterCaptor = ArgumentCaptor.forClass(RatingFilter.class);
            verify(getUserRatingHandler).execute(filterCaptor.capture(), eq(userId), anyInt(), anyInt());

            RatingFilter capturedFilter = filterCaptor.getValue();
            assertThat(capturedFilter.from()).isEqualTo(fromDate);
            assertThat(capturedFilter.to()).isEqualTo(toDate);
        }

        @Test
        @DisplayName("whenNoRatings_shouldReturnEmptyList")
        void whenNoRatings_shouldReturnEmptyList() {
            // Arrange
            UUID userId = UUID.randomUUID();
            Page<Rating> emptyPage = new PageImpl<>(List.of());

            when(getUserRatingHandler.execute(any(RatingFilter.class), eq(userId), anyInt(), anyInt()))
                    .thenReturn(emptyPage);

            // Act
            List<RatingGetResponse> result = ratingService.findAllOfUser(
                    userId, null, null, null, null, 0, 10
            );

            // Assert
            assertThat(result).isEmpty();
            verify(ratingMapper, never()).toGetResponse(any(Rating.class));
        }

        @Test
        @DisplayName("whenDifferentPageRequested_shouldPassCorrectPaginationParameters")
        void whenDifferentPageRequested_shouldPassCorrectPaginationParameters() {
            // Arrange
            UUID userId = UUID.randomUUID();
            int pageNumber = 2;
            int pageSize = 25;

            Page<Rating> emptyPage = new PageImpl<>(List.of());
            when(getUserRatingHandler.execute(any(RatingFilter.class), eq(userId), anyInt(), anyInt()))
                    .thenReturn(emptyPage);

            // Act
            ratingService.findAllOfUser(userId, null, null, null, null, pageNumber, pageSize);

            // Assert
            verify(getUserRatingHandler)
                    .execute(any(RatingFilter.class), eq(userId), eq(pageSize), eq(pageNumber));
        }
    }

    @Nested
    @DisplayName("getMediaStatus() method tests")
    class GetMediaStatusTests {

        @Test
        @DisplayName("whenValidMediaId_shouldReturnMediaStatus")
        void whenValidMediaId_shouldReturnMediaStatus() {
            // Arrange
            UUID mediaId = UUID.randomUUID();

            EngagementStats engagementStats = mock(EngagementStats.class);

            GetMediaStatusResponse expectedResponse = new GetMediaStatusResponse(
                    mediaId,
                    4.5,
                    200L
            );

            when(getMediaStatsHandler.execute(mediaId)).thenReturn(engagementStats);
            when(ratingMapper.toMediaStatusResponse(engagementStats, mediaId)).thenReturn(expectedResponse);

            // Act
            GetMediaStatusResponse result = ratingService.getMediaStatus(mediaId);

            // Assert
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(result.mediaId()).isEqualTo(mediaId);
            assertThat(result.averageRating()).isEqualTo(4.5);
            assertThat(result.totalRatings()).isEqualTo(200L);

            verify(getMediaStatsHandler, times(1)).execute(mediaId);
            verify(ratingMapper, times(1)).toMediaStatusResponse(engagementStats, mediaId);
        }

        @Test
        @DisplayName("whenMediaHasNoRatings_shouldReturnZeroStats")
        void whenMediaHasNoRatings_shouldReturnZeroStats() {
            // Arrange
            UUID mediaId = UUID.randomUUID();

            EngagementStats emptyStats = mock(EngagementStats.class);

            GetMediaStatusResponse expectedResponse = new GetMediaStatusResponse(
                    mediaId,
                    0.0,
                    0L
            );

            when(getMediaStatsHandler.execute(mediaId)).thenReturn(emptyStats);
            when(ratingMapper.toMediaStatusResponse(emptyStats, mediaId)).thenReturn(expectedResponse);

            // Act
            GetMediaStatusResponse result = ratingService.getMediaStatus(mediaId);

            // Assert
            assertThat(result.totalRatings()).isZero();
            assertThat(result.averageRating()).isZero();
        }
    }
}
