package com.mrs.recommendation_service.module.user_profile.domain.use_case;

import com.mrs.recommendation_service.module.book_feature.domain.exception.BookFeatureNotFoundException;
import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.mrs.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.mrs.recommendation_service.module.user_profile.domain.command.UpdateUserProfileCommand;
import com.mrs.recommendation_service.module.user_profile.domain.model.UserProfile;
import com.mrs.recommendation_service.module.user_profile.domain.port.UserProfileRepository;
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
        BookFeature bookFeature = bookFeatureRepository.findById(updateUserProfileCommand.mediaId())
                .orElseThrow(() -> new BookFeatureNotFoundException(updateUserProfileCommand.mediaId().toString()));

        UserProfile userProfile = userProfileRepository.findById(updateUserProfileCommand.userId())
                .orElse(new UserProfile(updateUserProfileCommand.userId()));

        userProfile.processInteraction(
                bookFeature,
                updateUserProfileCommand.interactionType(),
                updateUserProfileCommand.interactionValue()
        );

        userProfileRepository.save(userProfile);
    }

}
