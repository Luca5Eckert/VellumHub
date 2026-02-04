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
    @DisplayName("Should update existing user profile when user and media feature exist")
    void shouldUpdateExistingUserProfile_WhenUserAndMediaFeatureExist() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        List<String> genres = List.of("ACTION", "COMEDY");

        MediaFeature mediaFeature = new MediaFeature(mediaId, genres);
        UserProfile existingUserProfile = new UserProfile(userId);

        UpdateUserProfileCommand command = new UpdateUserProfileCommand(
                userId,
                mediaId,
                InteractionType.LIKE,
                1.0
        );

        when(mediaFeatureRepository.findById(mediaId)).thenReturn(Optional.of(mediaFeature));
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(existingUserProfile));

        // Act
        updateUserProfileHandler.execute(command);

        // Assert
        ArgumentCaptor<UserProfile> userProfileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository, times(1)).save(userProfileCaptor.capture());

        UserProfile savedUserProfile = userProfileCaptor.getValue();
        assertEquals(userId, savedUserProfile.getUserId());
        assertTrue(savedUserProfile.getInteractedMediaIds().contains(mediaId));
    }

    @Test
    @DisplayName("Should create new user profile when user does not exist")
    void shouldCreateNewUserProfile_WhenUserDoesNotExist() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        List<String> genres = List.of("THRILLER");

        MediaFeature mediaFeature = new MediaFeature(mediaId, genres);

        UpdateUserProfileCommand command = new UpdateUserProfileCommand(
                userId,
                mediaId,
                InteractionType.WATCH,
                0.5
        );

        when(mediaFeatureRepository.findById(mediaId)).thenReturn(Optional.of(mediaFeature));
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        // Act
        updateUserProfileHandler.execute(command);

        // Assert
        ArgumentCaptor<UserProfile> userProfileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository, times(1)).save(userProfileCaptor.capture());

        UserProfile savedUserProfile = userProfileCaptor.getValue();
        assertEquals(userId, savedUserProfile.getUserId());
    }

    @Test
    @DisplayName("Should throw MediaFeatureNotFoundException when media feature does not exist")
    void shouldThrowException_WhenMediaFeatureDoesNotExist() {
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
        MediaFeatureNotFoundException exception = assertThrows(MediaFeatureNotFoundException.class, () ->
                updateUserProfileHandler.execute(command)
        );

        assertTrue(exception.getMessage().contains(mediaId.toString()));
        verify(mediaFeatureRepository, times(1)).findById(mediaId);
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    @DisplayName("Should process DISLIKE interaction correctly")
    void shouldProcessDislikeInteraction() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        List<String> genres = List.of("HORROR");

        MediaFeature mediaFeature = new MediaFeature(mediaId, genres);
        UserProfile existingUserProfile = new UserProfile(userId);

        UpdateUserProfileCommand command = new UpdateUserProfileCommand(
                userId,
                mediaId,
                InteractionType.DISLIKE,
                -1.0
        );

        when(mediaFeatureRepository.findById(mediaId)).thenReturn(Optional.of(mediaFeature));
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(existingUserProfile));

        // Act
        updateUserProfileHandler.execute(command);

        // Assert
        ArgumentCaptor<UserProfile> userProfileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository, times(1)).save(userProfileCaptor.capture());

        UserProfile savedUserProfile = userProfileCaptor.getValue();
        assertEquals(1, savedUserProfile.getTotalDislikes());
    }
}
