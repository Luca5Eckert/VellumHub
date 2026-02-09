package com.mrs.engagement_service.handler;

import com.mrs.engagement_service.module.rating.application.dto.filter.RatingFilter;
import com.mrs.engagement_service.module.rating.domain.handler.GetUserRatingHandler;
import com.mrs.engagement_service.module.book_progress.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.port.EngagementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest; // Import PageRequest

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetUserRatingHandlerTest {

    @Mock
    private EngagementRepository engagementRepository;

    @InjectMocks
    private GetUserRatingHandler getUserRatingHandler;

    @Test
    @DisplayName("Should return a page of ratings when repository execution is successful")
    void shouldReturnRatingsPage_WhenRepositoryFindsData() {
        // Arrange
        UUID userId = UUID.randomUUID();
        int pageSize = 10;
        int pageNumber = 0;

        RatingFilter filter = new RatingFilter(
                3,
                5,
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now()
        );

        Page<Rating> expectedPage = new PageImpl<>(Collections.emptyList());

        when(engagementRepository.findAll(eq(userId), eq(filter), any(PageRequest.class)))
                .thenReturn(expectedPage);

        // Act
        Page<Rating> result = getUserRatingHandler.execute(filter, userId, pageSize, pageNumber);

        // Assert
        assertNotNull(result, "The result should not be null");
        assertEquals(expectedPage, result, "The returned page should match the repository result");

        verify(engagementRepository, times(1))
                .findAll(eq(userId), eq(filter), any(PageRequest.class));
    }

    @Test
    @DisplayName("Should create correct PageRequest with provided page number and size")
    void shouldCreateCorrectPageRequest() {
        // Arrange
        UUID userId = UUID.randomUUID();
        RatingFilter filter = new RatingFilter(null, null, null, null);
        int expectedPageSize = 20;
        int expectedPageNumber = 1;

        ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);

        // Act
        getUserRatingHandler.execute(filter, userId, expectedPageSize, expectedPageNumber);

        // Assert & Verify
        verify(engagementRepository).findAll(eq(userId), eq(filter), pageRequestCaptor.capture());

        PageRequest capturedRequest = pageRequestCaptor.getValue();

        assertEquals(expectedPageNumber, capturedRequest.getPageNumber(), "Page number should match input");
        assertEquals(expectedPageSize, capturedRequest.getPageSize(), "Page size should match input");
    }
}