package com.mrs.engagement_service.service;

import com.mrs.engagement_service.application.dto.GetMediaStatusResponse;
import com.mrs.engagement_service.application.dto.InteractionCreateRequest;
import com.mrs.engagement_service.application.dto.InteractionGetResponse;
import com.mrs.engagement_service.application.dto.filter.InteractionFilter;
import com.mrs.engagement_service.domain.handler.CreateEngagementHandler;
import com.mrs.engagement_service.domain.handler.GetMediaStatsHandler;
import com.mrs.engagement_service.domain.handler.GetUserInteractionHandler;
import com.mrs.engagement_service.domain.model.EngagementStats;
import com.mrs.engagement_service.domain.model.Interaction;
import com.mrs.engagement_service.domain.model.InteractionType;
import com.mrs.engagement_service.domain.port.InteractionMapper;
import com.mrs.engagement_service.domain.service.EngagementService;
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
 * Unit tests for EngagementService.
 * Tests cover all service methods with mocked handlers.
 * Follows the pattern: condition_expectedBehavior.
 */
@ExtendWith(MockitoExtension.class)
class EngagementServiceTest {

    @Mock
    private CreateEngagementHandler createEngagementHandler;

    @Mock
    private GetUserInteractionHandler getUserInteractionHandler;

    @Mock
    private GetMediaStatsHandler getMediaStatsHandler;

    @Mock
    private InteractionMapper interactionMapper;

    @InjectMocks
    private EngagementService engagementService;

    @Nested
    @DisplayName("create() method tests")
    class CreateTests {

        @Test
        @DisplayName("whenValidLikeInteraction_shouldCreateInteraction")
        void whenValidLikeInteraction_shouldCreateInteraction() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();

            InteractionCreateRequest request = new InteractionCreateRequest(
                    userId,
                    mediaId,
                    InteractionType.LIKE,
                    1.0
            );

            // Act
            engagementService.create(request);

            // Assert
            ArgumentCaptor<Interaction> interactionCaptor = ArgumentCaptor.forClass(Interaction.class);
            verify(createEngagementHandler, times(1)).handler(interactionCaptor.capture());

            Interaction capturedInteraction = interactionCaptor.getValue();
            assertThat(capturedInteraction.getUserId()).isEqualTo(userId);
            assertThat(capturedInteraction.getMediaId()).isEqualTo(mediaId);
            assertThat(capturedInteraction.getType()).isEqualTo(InteractionType.LIKE);
            assertThat(capturedInteraction.getInteractionValue()).isEqualTo(1.0);
            assertThat(capturedInteraction.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("whenValidRatingInteraction_shouldCreateInteractionWithValue")
        void whenValidRatingInteraction_shouldCreateInteractionWithValue() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();

            InteractionCreateRequest request = new InteractionCreateRequest(
                    userId,
                    mediaId,
                    InteractionType.LIKE,
                    4.5
            );

            // Act
            engagementService.create(request);

            // Assert
            ArgumentCaptor<Interaction> interactionCaptor = ArgumentCaptor.forClass(Interaction.class);
            verify(createEngagementHandler).handler(interactionCaptor.capture());

            Interaction capturedInteraction = interactionCaptor.getValue();
            assertThat(capturedInteraction.getType()).isEqualTo(InteractionType.LIKE);
            assertThat(capturedInteraction.getInteractionValue()).isEqualTo(4.5);
        }

        @Test
        @DisplayName("whenValidViewInteraction_shouldCreateInteraction")
        void whenValidViewInteraction_shouldCreateInteraction() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();

            InteractionCreateRequest request = new InteractionCreateRequest(
                    userId,
                    mediaId,
                    InteractionType.WATCH,
                    1.0
            );

            // Act
            engagementService.create(request);

            // Assert
            ArgumentCaptor<Interaction> interactionCaptor = ArgumentCaptor.forClass(Interaction.class);
            verify(createEngagementHandler).handler(interactionCaptor.capture());

            Interaction capturedInteraction = interactionCaptor.getValue();
            assertThat(capturedInteraction.getType()).isEqualTo(InteractionType.WATCH);
        }
    }

    @Nested
    @DisplayName("findAllOfUser() method tests")
    class FindAllOfUserTests {

        @Test
        @DisplayName("whenUserHasInteractions_shouldReturnInteractionList")
        void whenUserHasInteractions_shouldReturnInteractionList() {
            // Arrange
            UUID userId = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();

            Interaction interaction1 = new Interaction(
                    userId,
                    UUID.randomUUID(),
                    InteractionType.LIKE,
                    1.0,
                    now
            );

            Interaction interaction2 = new Interaction(
                    userId,
                    UUID.randomUUID(),
                    InteractionType.WATCH,
                    1.0,
                    now
            );

            Page<Interaction> interactionsPage = new PageImpl<>(List.of(interaction1, interaction2));

            InteractionGetResponse response1 = new InteractionGetResponse(
                    1L, userId, interaction1.getMediaId(), InteractionType.LIKE, 1.0, now
            );
            InteractionGetResponse response2 = new InteractionGetResponse(
                    2L, userId, interaction2.getMediaId(), InteractionType.WATCH, 1.0, now
            );

            when(getUserInteractionHandler.execute(any(InteractionFilter.class), eq(userId), anyInt(), anyInt()))
                    .thenReturn(interactionsPage);
            when(interactionMapper.toGetResponse(interaction1)).thenReturn(response1);
            when(interactionMapper.toGetResponse(interaction2)).thenReturn(response2);

            // Act
            List<InteractionGetResponse> result = engagementService.findAllOfUser(
                    userId, null, null, null, 0, 10
            );

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).type()).isEqualTo(InteractionType.LIKE);
            assertThat(result.get(1).type()).isEqualTo(InteractionType.WATCH);

            verify(getUserInteractionHandler, times(1))
                    .execute(any(InteractionFilter.class), eq(userId), eq(0), eq(10));
        }

        @Test
        @DisplayName("whenTypeFilterProvided_shouldCreateFilterWithType")
        void whenTypeFilterProvided_shouldCreateFilterWithType() {
            // Arrange
            UUID userId = UUID.randomUUID();
            InteractionType typeFilter = InteractionType.LIKE;

            Page<Interaction> emptyPage = new PageImpl<>(List.of());
            when(getUserInteractionHandler.execute(any(InteractionFilter.class), eq(userId), anyInt(), anyInt()))
                    .thenReturn(emptyPage);

            // Act
            engagementService.findAllOfUser(userId, typeFilter, null, null, 0, 10);

            // Assert
            ArgumentCaptor<InteractionFilter> filterCaptor = ArgumentCaptor.forClass(InteractionFilter.class);
            verify(getUserInteractionHandler).execute(filterCaptor.capture(), eq(userId), anyInt(), anyInt());

            InteractionFilter capturedFilter = filterCaptor.getValue();
            assertThat(capturedFilter.type()).isEqualTo(InteractionType.LIKE);
        }

        @Test
        @DisplayName("whenDateFiltersProvided_shouldCreateFilterWithDates")
        void whenDateFiltersProvided_shouldCreateFilterWithDates() {
            // Arrange
            UUID userId = UUID.randomUUID();
            OffsetDateTime fromDate = OffsetDateTime.now().minusDays(7);
            OffsetDateTime toDate = OffsetDateTime.now();

            Page<Interaction> emptyPage = new PageImpl<>(List.of());
            when(getUserInteractionHandler.execute(any(InteractionFilter.class), eq(userId), anyInt(), anyInt()))
                    .thenReturn(emptyPage);

            // Act
            engagementService.findAllOfUser(userId, null, fromDate, toDate, 0, 10);

            // Assert
            ArgumentCaptor<InteractionFilter> filterCaptor = ArgumentCaptor.forClass(InteractionFilter.class);
            verify(getUserInteractionHandler).execute(filterCaptor.capture(), eq(userId), anyInt(), anyInt());

            InteractionFilter capturedFilter = filterCaptor.getValue();
            assertThat(capturedFilter.from()).isEqualTo(fromDate);
            assertThat(capturedFilter.to()).isEqualTo(toDate);
        }

        @Test
        @DisplayName("whenNoInteractions_shouldReturnEmptyList")
        void whenNoInteractions_shouldReturnEmptyList() {
            // Arrange
            UUID userId = UUID.randomUUID();
            Page<Interaction> emptyPage = new PageImpl<>(List.of());

            when(getUserInteractionHandler.execute(any(InteractionFilter.class), eq(userId), anyInt(), anyInt()))
                    .thenReturn(emptyPage);

            // Act
            List<InteractionGetResponse> result = engagementService.findAllOfUser(
                    userId, null, null, null, 0, 10
            );

            // Assert
            assertThat(result).isEmpty();
            verify(interactionMapper, never()).toGetResponse(any(Interaction.class));
        }

        @Test
        @DisplayName("whenDifferentPageRequested_shouldPassCorrectPaginationParameters")
        void whenDifferentPageRequested_shouldPassCorrectPaginationParameters() {
            // Arrange
            UUID userId = UUID.randomUUID();
            int pageNumber = 2;
            int pageSize = 25;

            Page<Interaction> emptyPage = new PageImpl<>(List.of());
            when(getUserInteractionHandler.execute(any(InteractionFilter.class), eq(userId), anyInt(), anyInt()))
                    .thenReturn(emptyPage);

            // Act
            engagementService.findAllOfUser(userId, null, null, null, pageNumber, pageSize);

            // Assert
            verify(getUserInteractionHandler)
                    .execute(any(InteractionFilter.class), eq(userId), eq(pageNumber), eq(pageSize));
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
                    1000L, 500L, 50L, 4.5, 200L, 1750L, 85.5
            );

            when(getMediaStatsHandler.execute(mediaId)).thenReturn(engagementStats);
            when(interactionMapper.toMediaStatusResponse(engagementStats, mediaId)).thenReturn(expectedResponse);

            // Act
            GetMediaStatusResponse result = engagementService.getMediaStatus(mediaId);

            // Assert
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(result.mediaId()).isEqualTo(mediaId);
            assertThat(result.totalViews()).isEqualTo(1000L);
            assertThat(result.totalLikes()).isEqualTo(500L);
            assertThat(result.averageRating()).isEqualTo(4.5);
            assertThat(result.popularityScore()).isEqualTo(85.5);

            verify(getMediaStatsHandler, times(1)).execute(mediaId);
            verify(interactionMapper, times(1)).toMediaStatusResponse(engagementStats, mediaId);
        }

        @Test
        @DisplayName("whenMediaHasNoInteractions_shouldReturnZeroStats")
        void whenMediaHasNoInteractions_shouldReturnZeroStats() {
            // Arrange
            UUID mediaId = UUID.randomUUID();

            EngagementStats emptyStats = mock(EngagementStats.class);

            GetMediaStatusResponse expectedResponse = new GetMediaStatusResponse(
                    mediaId,
                    0L, 0L, 0L, 0.0, 0L, 0L, 0.0
            );

            when(getMediaStatsHandler.execute(mediaId)).thenReturn(emptyStats);
            when(interactionMapper.toMediaStatusResponse(emptyStats, mediaId)).thenReturn(expectedResponse);

            // Act
            GetMediaStatusResponse result = engagementService.getMediaStatus(mediaId);

            // Assert
            assertThat(result.totalViews()).isZero();
            assertThat(result.totalInteractions()).isZero();
            assertThat(result.popularityScore()).isZero();
        }
    }
}
