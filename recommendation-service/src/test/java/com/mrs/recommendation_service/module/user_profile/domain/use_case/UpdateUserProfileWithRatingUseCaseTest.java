package com.mrs.recommendation_service.module.user_profile.domain.use_case;

import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.mrs.recommendation_service.module.book_feature.domain.model.Genre;
import com.mrs.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.mrs.recommendation_service.module.user_profile.domain.command.UpdateUserProfileWithRatingCommand;
import com.mrs.recommendation_service.module.user_profile.domain.model.UserProfile;
import com.mrs.recommendation_service.module.user_profile.domain.port.UserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateUserProfileWithRatingUseCaseTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private BookFeatureRepository bookFeatureRepository;

    @InjectMocks
    private UpdateUserProfileWithRatingUseCase updateUserProfileWithRatingUseCase;

    @Test
    @DisplayName("Should create new user profile and update it when profile does not exist")
    void shouldCreateNewProfileWhenNotExists() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        UpdateUserProfileWithRatingCommand command = new UpdateUserProfileWithRatingCommand(userId, mediaId, 0, 5, true);

        float[] embedding = new float[Genre.total()];
        embedding[Genre.FANTASY.index] = 1.0f;
        BookFeature bookFeature = new BookFeature(mediaId, embedding);

        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());
        when(bookFeatureRepository.findById(mediaId)).thenReturn(Optional.of(bookFeature));

        // Act
        updateUserProfileWithRatingUseCase.execute(command);

        // Assert
        verify(userProfileRepository, times(1)).findById(userId);
        verify(bookFeatureRepository, times(1)).findById(mediaId);
        verify(userProfileRepository, times(1)).save(any(UserProfile.class));
    }

    @Test
    @DisplayName("Should update existing user profile when it already exists")
    void shouldUpdateExistingProfile() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        UpdateUserProfileWithRatingCommand command = new UpdateUserProfileWithRatingCommand(userId, mediaId, 2, 5, false);

        UserProfile existingProfile = new UserProfile(userId);
        float[] embedding = new float[Genre.total()];
        BookFeature bookFeature = new BookFeature(mediaId, embedding);

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(existingProfile));
        when(bookFeatureRepository.findById(mediaId)).thenReturn(Optional.of(bookFeature));

        // Act
        updateUserProfileWithRatingUseCase.execute(command);

        // Assert
        verify(userProfileRepository, times(1)).save(existingProfile);
    }

    @Test
    @DisplayName("Should throw exception when book feature is not found")
    void shouldThrowExceptionWhenBookFeatureNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        UpdateUserProfileWithRatingCommand command = new UpdateUserProfileWithRatingCommand(userId, mediaId, 0, 4, true);

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(new UserProfile(userId)));
        when(bookFeatureRepository.findById(mediaId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> updateUserProfileWithRatingUseCase.execute(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Book features not found");

        verify(userProfileRepository, never()).save(any());
    }
}
