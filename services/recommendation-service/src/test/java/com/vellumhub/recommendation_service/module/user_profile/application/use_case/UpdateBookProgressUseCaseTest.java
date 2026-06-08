package com.vellumhub.recommendation_service.module.user_profile.application.use_case;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateBookProgressCommand;
import com.vellumhub.recommendation_service.module.user_profile.domain.interaction.progress.BookProgressInteraction;
import com.vellumhub.recommendation_service.module.user_profile.domain.interaction.progress.Progress;
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
class UpdateBookProgressUseCaseTest {

    private static final float[] EMBEDDING = new float[384];

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private BookFeatureRepository bookFeatureRepository;

    @Mock
    private BookProgressInteraction bookProgressInteraction;

    @InjectMocks
    private UpdateBookProgressUseCase updateBookProgressUseCase;

    private UUID userId;
    private UUID bookId;
    private BookFeature bookFeature;
    private UpdateBookProgressCommand command;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        bookId = UUID.randomUUID();
        bookFeature = BookFeature.create(bookId, EMBEDDING, 1.0);
        command = UpdateBookProgressCommand.of(userId, bookId, Progress.READING.name(), 0, 50);
    }

    @Test
    void execute_whenProfileExists_shouldLoadExistingProfile() {
        UserProfile existingProfile = new UserProfile(userId);
        ProfileAdjustment adjustment = new ProfileAdjustment(bookId, 1.0f, EMBEDDING);

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(existingProfile));
        when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
        when(bookProgressInteraction.toAdjustment(bookFeature, command.progress(), command.oldPage(), command.newPage())).thenReturn(adjustment);

        updateBookProgressUseCase.execute(command);

        verify(userProfileRepository).findById(userId);
        verify(userProfileRepository, never()).save(argThat(p -> !p.getUserId().equals(userId)));
    }

    @Test
    void execute_whenProfileDoesNotExist_shouldCreateNewProfile() {
        ProfileAdjustment adjustment = new ProfileAdjustment(bookId, 1.0f, EMBEDDING);

        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());
        when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
        when(bookProgressInteraction.toAdjustment(bookFeature, command.progress(), command.oldPage(), command.newPage())).thenReturn(adjustment);

        updateBookProgressUseCase.execute(command);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void execute_whenBookNotFound_shouldSkipProfileUpdate() {
        when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.empty());

        updateBookProgressUseCase.execute(command);

        verify(userProfileRepository, never()).findById(any());
        verify(userProfileRepository, never()).save(any());
        verify(bookProgressInteraction, never()).toAdjustment(any(), any(), anyInt(), anyInt());
    }

    @Test
    void execute_shouldDelegateAdjustmentCalculationToInteraction() {
        ProfileAdjustment adjustment = new ProfileAdjustment(bookId, 1.0f, EMBEDDING);

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(new UserProfile(userId)));
        when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
        when(bookProgressInteraction.toAdjustment(bookFeature, command.progress(), command.oldPage(), command.newPage())).thenReturn(adjustment);

        updateBookProgressUseCase.execute(command);

        verify(bookProgressInteraction).toAdjustment(bookFeature, command.progress(), command.oldPage(), command.newPage());
    }

    @Test
    void execute_shouldSaveProfileAfterApplyingAdjustment() {
        ProfileAdjustment adjustment = new ProfileAdjustment(bookId, 1.0f, EMBEDDING);

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(new UserProfile(userId)));
        when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
        when(bookProgressInteraction.toAdjustment(bookFeature, command.progress(), command.oldPage(), command.newPage())).thenReturn(adjustment);

        updateBookProgressUseCase.execute(command);

        verify(userProfileRepository).save(any(UserProfile.class));
    }
}
