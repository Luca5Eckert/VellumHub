package com.vellumhub.recommendation_service.module.user_profile.application.use_case;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.user_profile.application.command.ReactionChangedCommand;
import com.vellumhub.recommendation_service.module.user_profile.domain.interaction.reaction.Reaction;
import com.vellumhub.recommendation_service.module.user_profile.domain.interaction.reaction.ReactionBookInteraction;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReactionChangedUseCaseTest {

    private static final float[] EMBEDDING = new float[384];

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private BookFeatureRepository bookFeatureRepository;

    @Mock
    private ReactionBookInteraction reactionBookInteraction;

    @InjectMocks
    private ReactionChangedUseCase reactionChangedUseCase;

    private UUID userId;
    private UUID bookId;
    private BookFeature bookFeature;
    private ReactionChangedCommand command;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        bookId = UUID.randomUUID();
        bookFeature = BookFeature.create(bookId, EMBEDDING, 1.0);
        command = new ReactionChangedCommand(
                userId,
                bookId,
                Reaction.POSITIVE.name(),
                Reaction.VERY_POSITIVE.name()
        );
    }

    @Test
    void executeWhenProfileExistsShouldLoadExistingProfile() {
        UserProfile existingProfile = new UserProfile(userId);
        ProfileAdjustment adjustment = new ProfileAdjustment(bookId, 1.5f, EMBEDDING);

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(existingProfile));
        when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
        when(reactionBookInteraction.toAdjustment(
                bookFeature,
                command.oldReactionType(),
                command.newReactionType()
        )).thenReturn(adjustment);

        reactionChangedUseCase.execute(command);

        verify(userProfileRepository).findById(userId);
        verify(userProfileRepository).save(existingProfile);
    }

    @Test
    void executeWhenProfileDoesNotExistShouldCreateNewProfile() {
        ReactionChangedCommand creation = new ReactionChangedCommand(
                userId,
                bookId,
                null,
                Reaction.POSITIVE.name()
        );
        ProfileAdjustment adjustment = new ProfileAdjustment(bookId, Reaction.POSITIVE.adjustmentValue, EMBEDDING);

        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());
        when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
        when(reactionBookInteraction.toAdjustment(
                bookFeature,
                creation.oldReactionType(),
                creation.newReactionType()
        )).thenReturn(adjustment);

        reactionChangedUseCase.execute(creation);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void executeWhenBookNotFoundShouldSkipProfileUpdate() {
        when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.empty());

        reactionChangedUseCase.execute(command);

        verify(userProfileRepository, never()).findById(any());
        verify(userProfileRepository, never()).save(any());
        verify(reactionBookInteraction, never()).toAdjustment(any(), any(), any());
    }

    @Test
    void executeShouldDelegateCompleteTransitionToInteraction() {
        ProfileAdjustment adjustment = new ProfileAdjustment(bookId, 1.5f, EMBEDDING);

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(new UserProfile(userId)));
        when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
        when(reactionBookInteraction.toAdjustment(
                bookFeature,
                command.oldReactionType(),
                command.newReactionType()
        )).thenReturn(adjustment);

        reactionChangedUseCase.execute(command);

        verify(reactionBookInteraction).toAdjustment(
                bookFeature,
                Reaction.POSITIVE.name(),
                Reaction.VERY_POSITIVE.name()
        );
    }

    @Test
    void executeSameValueTransitionShouldPersistZeroDriftProfile() {
        ReactionChangedCommand unchanged = new ReactionChangedCommand(
                userId,
                bookId,
                Reaction.POSITIVE.name(),
                Reaction.POSITIVE.name()
        );
        UserProfile profile = UserProfile.create(userId);
        float[] vectorBefore = profile.getProfileVector().clone();
        ProfileAdjustment zeroAdjustment = new ProfileAdjustment(bookId, 0.0f, EMBEDDING);

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
        when(reactionBookInteraction.toAdjustment(
                bookFeature,
                unchanged.oldReactionType(),
                unchanged.newReactionType()
        )).thenReturn(zeroAdjustment);

        reactionChangedUseCase.execute(unchanged);

        assertThat(profile.getProfileVector()).containsExactly(vectorBefore);
        verify(userProfileRepository).save(profile);
    }
}
