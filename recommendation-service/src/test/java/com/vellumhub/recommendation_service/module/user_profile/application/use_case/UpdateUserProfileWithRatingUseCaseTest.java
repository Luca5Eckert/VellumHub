package com.vellumhub.recommendation_service.module.user_profile.application.use_case;

import com.vellumhub.recommendation_service.module.book_feature.domain.exception.BookFeatureNotFoundException;
import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateUserProfileWithRatingCommand;
import com.vellumhub.recommendation_service.module.user_profile.domain.interaction.rating.RatingBookInteraction;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.UserProfile;
import com.vellumhub.recommendation_service.module.user_profile.domain.port.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateUserProfileWithRatingUseCaseTest {

    private static final float[] EMBEDDING = new float[384];

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private BookFeatureRepository bookFeatureRepository;

    @Mock
    private RatingBookInteraction ratingBookInteraction;

    @InjectMocks
    private UpdateUserProfileWithRatingUseCase updateUserProfileWithRatingUseCase;

    private UUID userId;
    private UUID bookId;
    private BookFeature bookFeature;
    private UpdateUserProfileWithRatingCommand command;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        bookId = UUID.randomUUID();
        bookFeature = BookFeature.create(bookId, EMBEDDING, 1.0);
        command = new UpdateUserProfileWithRatingCommand(userId, bookId, 0, 5, true);
    }

    @Test
    void execute_whenProfileExists_shouldLoadExistingProfile() {
        UserProfile existingProfile = new UserProfile(userId);
        ProfileAdjustment adjustment = new ProfileAdjustment(bookId, 5.0f, EMBEDDING);

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(existingProfile));
        when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
        when(ratingBookInteraction.toAdjustment(bookFeature, command.oldStars(), command.newStars(), command.isNewRating())).thenReturn(adjustment);

        updateUserProfileWithRatingUseCase.execute(command);

        verify(userProfileRepository).findById(userId);
        verify(userProfileRepository, never()).save(argThat(p -> !p.getUserId().equals(userId)));
    }

    @Test
    void execute_whenProfileDoesNotExist_shouldCreateNewProfile() {
        ProfileAdjustment adjustment = new ProfileAdjustment(bookId, 5.0f, EMBEDDING);

        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());
        when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
        when(ratingBookInteraction.toAdjustment(bookFeature, command.oldStars(), command.newStars(), command.isNewRating())).thenReturn(adjustment);

        updateUserProfileWithRatingUseCase.execute(command);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void execute_whenBookNotFound_shouldThrowBookFeatureNotFoundException() {
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(new UserProfile(userId)));
        when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateUserProfileWithRatingUseCase.execute(command))
                .isInstanceOf(BookFeatureNotFoundException.class);

        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void execute_shouldDelegateAdjustmentCalculationToInteraction() {
        ProfileAdjustment adjustment = new ProfileAdjustment(bookId, 5.0f, EMBEDDING);

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(new UserProfile(userId)));
        when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
        when(ratingBookInteraction.toAdjustment(bookFeature, command.oldStars(), command.newStars(), command.isNewRating())).thenReturn(adjustment);

        updateUserProfileWithRatingUseCase.execute(command);

        verify(ratingBookInteraction).toAdjustment(bookFeature, command.oldStars(), command.newStars(), command.isNewRating());
    }

    @Test
    void execute_shouldSaveProfileAfterApplyingAdjustment() {
        ProfileAdjustment adjustment = new ProfileAdjustment(bookId, 5.0f, EMBEDDING);

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(new UserProfile(userId)));
        when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
        when(ratingBookInteraction.toAdjustment(bookFeature, command.oldStars(), command.newStars(), command.isNewRating())).thenReturn(adjustment);

        updateUserProfileWithRatingUseCase.execute(command);

        verify(userProfileRepository).save(any(UserProfile.class));
    }
}