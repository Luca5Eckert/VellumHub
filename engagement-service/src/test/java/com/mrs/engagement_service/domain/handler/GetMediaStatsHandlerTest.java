package com.mrs.engagement_service.domain.handler;

import com.mrs.engagement_service.domain.model.EngagementStats;
import com.mrs.engagement_service.domain.port.EngagementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetMediaStatsHandlerTest {

    @Mock
    private EngagementRepository engagementRepository;

    @InjectMocks
    private GetMediaStatsHandler getMediaStatsHandler;

    private UUID mediaId;
    private EngagementStats mockStatus;

    @BeforeEach
    void setUp() {
        mediaId = UUID.randomUUID();
        mockStatus = mock(EngagementStats.class);
    }

    @Test
    @DisplayName("Should return media statistics successfully")
    void shouldReturnMediaStatsSuccessfully() {
        // Arrange
        when(mockStatus.getTotalRatings()).thenReturn(150L);
        when(mockStatus.getAverageRating()).thenReturn(4.5);
        when(engagementRepository.findStatusByMediaId(mediaId)).thenReturn(mockStatus);

        // Act
        EngagementStats result = getMediaStatsHandler.execute(mediaId);

        // Assert
        assertNotNull(result);
        assertEquals(150L, result.getTotalRatings());
        assertEquals(4.5, result.getAverageRating());
        verify(engagementRepository, times(1)).findStatusByMediaId(mediaId);
    }

    @Test
    @DisplayName("Should return empty status or null when no ratings found")
    void shouldHandleEmptyResults() {
        // Arrange
        when(engagementRepository.findStatusByMediaId(mediaId)).thenReturn(null);

        // Act
        EngagementStats result = getMediaStatsHandler.execute(mediaId);

        // Assert
        assertNull(result);
        verify(engagementRepository, times(1)).findStatusByMediaId(mediaId);
    }
    
}