package com.mrs.recommendation_service.domain.handler.media_feature;

import com.mrs.recommendation_service.domain.exception.user_profile.UserProfileNotFoundException;
import com.mrs.recommendation_service.domain.model.UserProfile;
import com.mrs.recommendation_service.domain.port.UserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetRecommendationsHandlerTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private RestClient restClient;

    @InjectMocks
    private GetRecommendationsHandler getRecommendationsHandler;

    @Test
    @DisplayName("Should throw UserProfileNotFoundException when user profile does not exist")
    void shouldThrowException_WhenUserProfileNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(UserProfileNotFoundException.class, () ->
                getRecommendationsHandler.execute(userId)
        );

        verify(userProfileRepository, times(1)).findById(userId);
        verifyNoInteractions(restClient);
    }
}
