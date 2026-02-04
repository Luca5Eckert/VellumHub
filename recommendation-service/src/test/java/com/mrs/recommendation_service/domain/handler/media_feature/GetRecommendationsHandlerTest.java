package com.mrs.recommendation_service.domain.handler.media_feature;

import com.mrs.recommendation_service.application.dto.GetRecommendationRequest;
import com.mrs.recommendation_service.application.dto.RecommendationMlResponse;
import com.mrs.recommendation_service.domain.exception.user_profile.UserProfileNotFoundException;
import com.mrs.recommendation_service.domain.model.Recommendation;
import com.mrs.recommendation_service.domain.model.UserProfile;
import com.mrs.recommendation_service.domain.port.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetRecommendationsHandlerTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private GetRecommendationsHandler getRecommendationsHandler;

    @BeforeEach
    void setUp() {
        getRecommendationsHandler = new GetRecommendationsHandler(userProfileRepository, restClient);
        ReflectionTestUtils.setField(getRecommendationsHandler, "mlServiceUrl", "http://ml-service:5000");
    }

    @Test
    @DisplayName("Should throw UserProfileNotFoundException when user profile does not exist")
    void shouldThrowException_WhenUserProfileDoesNotExist() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        UserProfileNotFoundException exception = assertThrows(UserProfileNotFoundException.class, () ->
                getRecommendationsHandler.execute(userId)
        );

        assertTrue(exception.getMessage().contains(userId.toString()));
        verify(userProfileRepository, times(1)).findById(userId);
        verifyNoInteractions(restClient);
    }

    @Test
    @DisplayName("Should return recommendations when user profile exists")
    void shouldReturnRecommendations_WhenUserProfileExists() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID mediaId1 = UUID.randomUUID();
        UUID mediaId2 = UUID.randomUUID();

        UserProfile userProfile = new UserProfile(userId);
        
        List<Recommendation> expectedRecommendations = List.of(
                new Recommendation(mediaId1, List.of("ACTION", "COMEDY"), 85.0, 90.0, 88.0),
                new Recommendation(mediaId2, List.of("THRILLER"), 75.0, 80.0, 77.0)
        );

        RecommendationMlResponse mlResponse = new RecommendationMlResponse(userId, expectedRecommendations, 2);

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(userProfile));
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(GetRecommendationRequest.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(RecommendationMlResponse.class)).thenReturn(mlResponse);

        // Act
        List<Recommendation> result = getRecommendationsHandler.execute(userId);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(expectedRecommendations, result);
        
        verify(userProfileRepository, times(1)).findById(userId);
        verify(restClient, times(1)).post();
    }
}
