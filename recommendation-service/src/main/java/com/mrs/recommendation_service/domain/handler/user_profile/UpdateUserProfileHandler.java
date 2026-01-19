package com.mrs.recommendation_service.domain.handler.user_profile;

import com.mrs.recommendation_service.domain.command.UpdateUserProfileCommand;
import com.mrs.recommendation_service.domain.exception.media_feature.MediaFeatureNotFoundException;
import com.mrs.recommendation_service.domain.model.MediaFeature;
import com.mrs.recommendation_service.domain.model.UserProfile;
import com.mrs.recommendation_service.domain.port.MediaFeatureRepository;
import com.mrs.recommendation_service.domain.port.UserProfileRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UpdateUserProfileHandler {

    private final MediaFeatureRepository mediaFeatureRepository;
    private final UserProfileRepository userProfileRepository;

    public UpdateUserProfileHandler(MediaFeatureRepository mediaFeatureRepository, UserProfileRepository userProfileRepository) {
        this.mediaFeatureRepository = mediaFeatureRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional
    public void execute(UpdateUserProfileCommand updateUserProfileCommand){
        MediaFeature mediaInteraction = mediaFeatureRepository.findById(updateUserProfileCommand.mediaId())
                .orElseThrow(() -> new MediaFeatureNotFoundException(updateUserProfileCommand.mediaId().toString()));

        UserProfile userProfile = userProfileRepository.findById(updateUserProfileCommand.userId())
                .orElse(new UserProfile(updateUserProfileCommand.userId()));

        userProfile.processInteraction(
                mediaInteraction,
                updateUserProfileCommand.interactionType(),
                updateUserProfileCommand.interactionValue()
        );

        userProfileRepository.save(userProfile);
    }

}
