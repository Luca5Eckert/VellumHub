package com.vellumhub.recommendation_service.module.user_profile.application.use_case;

import com.vellumhub.recommendation_service.module.user_profile.application.command.CreatedUserProfileCommand;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.UserProfile;
import com.vellumhub.recommendation_service.module.user_profile.domain.port.EmbeddingUserProfileProvider;
import com.vellumhub.recommendation_service.module.user_profile.domain.port.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Use case for creating a user profile based on the user's preferences.
 */
@Service
public class CreateUserProfileUseCase {

    private final UserProfileRepository userProfileRepository;

    private final EmbeddingUserProfileProvider profileProvider;

    public CreateUserProfileUseCase(UserProfileRepository userProfileRepository, EmbeddingUserProfileProvider profileProvider) {
        this.userProfileRepository = userProfileRepository;
        this.profileProvider = profileProvider;
    }

    /**
     * Creates a user profile based on the user's preferences.
     * @param command the command containing the data for creating the user profile.
     */
    @Transactional
    public void execute(CreatedUserProfileCommand command) {
        var userProfile = userProfileRepository.findById(command.userId())
                .orElseGet(() -> UserProfile.create(command.userId()));

        var vectors = profileProvider.of(
                command.genres(),
                command.about()
        );

        userProfile.applyVectorLearning(vectors, 0.5f);

        userProfileRepository.save(userProfile);
    }



}
