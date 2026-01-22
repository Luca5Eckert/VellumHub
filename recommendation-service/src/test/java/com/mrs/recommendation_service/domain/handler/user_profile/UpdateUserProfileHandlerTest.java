package com.mrs.recommendation_service.domain.handler.user_profile;

import com.mrs.recommendation_service.domain.command.UpdateUserProfileCommand;
import com.mrs.recommendation_service.domain.exception.media_feature.MediaFeatureNotFoundException;
import com.mrs.recommendation_service.domain.model.InteractionType;
import com.mrs.recommendation_service.domain.model.MediaFeature;
import com.mrs.recommendation_service.domain.model.UserProfile;
import com.mrs.recommendation_service.domain.port.MediaFeatureRepository;
import com.mrs.recommendation_service.domain.port.UserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateUserProfileHandlerTest {

    @Mock
    private MediaFeatureRepository mediaFeatureRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private UpdateUserProfileHandler updateUserProfileHandler;

    @Test
    @DisplayName("Should update existing user profile with media interaction")
    void shouldUpdateExistingUserProfile_WithMediaInteraction() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        List<String> genres = List.of("ACTION", "THRILLER");

        MediaFeature mediaFeature = new MediaFeature(mediaId, genres);
        UserProfile existingProfile = new UserProfile(userId);
        
        UpdateUserProfileCommand command = new UpdateUserProfileCommand(
                userId,
                mediaId,
                InteractionType.LIKE,
                1.0
        );

        when(mediaFeatureRepository.findById(mediaId)).thenReturn(Optional.of(mediaFeature));
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(existingProfile));

        // Act
        updateUserProfileHandler.execute(command);

        // Assert
        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository, times(1)).save(captor.capture());

        UserProfile savedProfile = captor.getValue();
        assertEquals(userId, savedProfile.getUserId());
        assertEquals(1, savedProfile.getTotalLikes());
        assertTrue(savedProfile.getInteractedMediaIds().contains(mediaId));
    }

    @Test
    @DisplayName("Should create new user profile when it does not exist")
    void shouldCreateNewUserProfile_WhenNotExists() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        List<String> genres = List.of("COMEDY");

        MediaFeature mediaFeature = new MediaFeature(mediaId, genres);
        
        UpdateUserProfileCommand command = new UpdateUserProfileCommand(
                userId,
                mediaId,
                InteractionType.WATCH,
                0.8
        );

        when(mediaFeatureRepository.findById(mediaId)).thenReturn(Optional.of(mediaFeature));
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        // Act
        updateUserProfileHandler.execute(command);

        // Assert
        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository, times(1)).save(captor.capture());

        UserProfile savedProfile = captor.getValue();
        assertEquals(userId, savedProfile.getUserId());
        assertEquals(1, savedProfile.getTotalWatches());
        assertTrue(savedProfile.getInteractedMediaIds().contains(mediaId));
    }

    @Test
    @DisplayName("Should throw MediaFeatureNotFoundException when media feature does not exist")
    void shouldThrowException_WhenMediaFeatureNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        
        UpdateUserProfileCommand command = new UpdateUserProfileCommand(
                userId,
                mediaId,
                InteractionType.LIKE,
                1.0
        );

        when(mediaFeatureRepository.findById(mediaId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(MediaFeatureNotFoundException.class, () ->
                updateUserProfileHandler.execute(command)
        );

        verify(userProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should process dislike interaction correctly")
    void shouldProcessDislikeInteraction() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        List<String> genres = List.of("HORROR");

        MediaFeature mediaFeature = new MediaFeature(mediaId, genres);
        UserProfile existingProfile = new UserProfile(userId);
        
        UpdateUserProfileCommand command = new UpdateUserProfileCommand(
                userId,
                mediaId,
                InteractionType.DISLIKE,
                1.0
        );

        when(mediaFeatureRepository.findById(mediaId)).thenReturn(Optional.of(mediaFeature));
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(existingProfile));

        // Act
        updateUserProfileHandler.execute(command);

        // Assert
        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository, times(1)).save(captor.capture());

        UserProfile savedProfile = captor.getValue();
        assertEquals(1, savedProfile.getTotalDislikes());
    }
}
