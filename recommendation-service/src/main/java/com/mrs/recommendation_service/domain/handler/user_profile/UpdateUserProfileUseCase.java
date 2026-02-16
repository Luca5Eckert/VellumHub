package com.mrs.recommendation_service.domain.handler.user_profile;

import com.mrs.recommendation_service.domain.command.UpdateUserProfileCommand;
import com.mrs.recommendation_service.domain.exception.book_feature.BookFeatureNotFoundException;
import com.mrs.recommendation_service.domain.model.BookFeature;
import com.mrs.recommendation_service.domain.model.UserProfile;
import com.mrs.recommendation_service.domain.port.BookFeatureRepository;
import com.mrs.recommendation_service.domain.port.UserProfileRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UpdateUserProfileUseCase {

    private final BookFeatureRepository bookFeatureRepository;
    private final UserProfileRepository userProfileRepository;

    public UpdateUserProfileUseCase(BookFeatureRepository bookFeatureRepository, UserProfileRepository userProfileRepository) {
        this.bookFeatureRepository = bookFeatureRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional
    public void execute(UpdateUserProfileCommand updateUserProfileCommand){
        BookFeature mediaInteraction = bookFeatureRepository.findById(updateUserProfileCommand.mediaId())
                .orElseThrow(() -> new BookFeatureNotFoundException(updateUserProfileCommand.mediaId().toString()));

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
