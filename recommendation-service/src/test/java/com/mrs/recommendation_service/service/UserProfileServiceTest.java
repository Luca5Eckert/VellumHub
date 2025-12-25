package com.mrs.recommendation_service.service;

import com.mrs.recommendation_service.event.InteractionEvent;
import com.mrs.recommendation_service.model.InteractionType;
import com.mrs.recommendation_service.model.MediaFeature;
import com.mrs.recommendation_service.model.UserProfile;
import com.mrs.recommendation_service.repository.MediaFeatureRepository;
import com.mrs.recommendation_service.repository.UserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserProfileService Tests")
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private MediaFeatureRepository mediaFeatureRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    @Nested
    @DisplayName("update() method")
    class UpdateTests {

        @Test
        @DisplayName("Should create new user profile when it doesn't exist")
        void update_WhenUserProfileDoesNotExist_CreatesNewProfile() {
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();
            
            InteractionEvent event = new InteractionEvent(
                    1L,
                    userId,
                    mediaId,
                    InteractionType.LIKE,
                    1.0,
                    LocalDateTime.now()
            );

            MediaFeature mediaFeature = new MediaFeature();
            mediaFeature.setMediaId(mediaId);
            mediaFeature.setGenres(List.of("ACTION", "THRILLER"));

            when(mediaFeatureRepository.findById(mediaId)).thenReturn(Optional.of(mediaFeature));
            when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());
            when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

            userProfileService.update(event);

            ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
            verify(userProfileRepository, times(1)).save(profileCaptor.capture());
            
            UserProfile savedProfile = profileCaptor.getValue();
            assertThat(savedProfile.getUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("Should update existing user profile")
        void update_WhenUserProfileExists_UpdatesProfile() {
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();
            
            InteractionEvent event = new InteractionEvent(
                    1L,
                    userId,
                    mediaId,
                    InteractionType.LIKE,
                    1.0,
                    LocalDateTime.now()
            );

            MediaFeature mediaFeature = new MediaFeature();
            mediaFeature.setMediaId(mediaId);
            mediaFeature.setGenres(List.of("ACTION"));

            UserProfile existingProfile = new UserProfile(userId);

            when(mediaFeatureRepository.findById(mediaId)).thenReturn(Optional.of(mediaFeature));
            when(userProfileRepository.findById(userId)).thenReturn(Optional.of(existingProfile));
            when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

            userProfileService.update(event);

            verify(userProfileRepository, times(1)).save(existingProfile);
        }

        @Test
        @DisplayName("Should throw exception when media not found")
        void update_WhenMediaNotFound_ThrowsException() {
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();
            
            InteractionEvent event = new InteractionEvent(
                    1L,
                    userId,
                    mediaId,
                    InteractionType.LIKE,
                    1.0,
                    LocalDateTime.now()
            );

            when(mediaFeatureRepository.findById(mediaId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userProfileService.update(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Media not found");

            verify(userProfileRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should add media to interacted IDs")
        void update_AddsMediaToInteractedIds() {
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();
            
            InteractionEvent event = new InteractionEvent(
                    1L,
                    userId,
                    mediaId,
                    InteractionType.WATCH,
                    0.5,
                    LocalDateTime.now()
            );

            MediaFeature mediaFeature = new MediaFeature();
            mediaFeature.setMediaId(mediaId);
            mediaFeature.setGenres(List.of("HORROR"));

            UserProfile userProfile = new UserProfile(userId);

            when(mediaFeatureRepository.findById(mediaId)).thenReturn(Optional.of(mediaFeature));
            when(userProfileRepository.findById(userId)).thenReturn(Optional.of(userProfile));
            when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

            userProfileService.update(event);

            ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
            verify(userProfileRepository).save(profileCaptor.capture());
            
            assertThat(profileCaptor.getValue().getInteractedMediaIds()).contains(mediaId);
        }

        @Test
        @DisplayName("Should update genre scores based on interaction")
        void update_UpdatesGenreScores() {
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();
            
            InteractionEvent event = new InteractionEvent(
                    1L,
                    userId,
                    mediaId,
                    InteractionType.LIKE,
                    1.0,
                    LocalDateTime.now()
            );

            MediaFeature mediaFeature = new MediaFeature();
            mediaFeature.setMediaId(mediaId);
            mediaFeature.setGenres(List.of("ACTION", "THRILLER"));

            UserProfile userProfile = new UserProfile(userId);

            when(mediaFeatureRepository.findById(mediaId)).thenReturn(Optional.of(mediaFeature));
            when(userProfileRepository.findById(userId)).thenReturn(Optional.of(userProfile));
            when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

            userProfileService.update(event);

            ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
            verify(userProfileRepository).save(profileCaptor.capture());
            
            UserProfile savedProfile = profileCaptor.getValue();
            assertThat(savedProfile.getGenreScores()).containsKey("ACTION");
            assertThat(savedProfile.getGenreScores()).containsKey("THRILLER");
        }
    }
}
