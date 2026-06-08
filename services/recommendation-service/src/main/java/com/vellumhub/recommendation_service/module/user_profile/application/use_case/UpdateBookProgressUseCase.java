package com.vellumhub.recommendation_service.module.user_profile.application.use_case;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateBookProgressCommand;
import com.vellumhub.recommendation_service.module.user_profile.domain.interaction.progress.BookProgressInteraction;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.UserProfile;
import com.vellumhub.recommendation_service.module.user_profile.domain.port.UserProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UpdateBookProgressUseCase {

    private final UserProfileRepository userProfileRepository;
    private final BookFeatureRepository bookFeatureRepository;

    private final BookProgressInteraction bookProgressInteraction;

    public UpdateBookProgressUseCase(UserProfileRepository userProfileRepository, BookFeatureRepository bookFeatureRepository, BookProgressInteraction bookProgressInteraction) {
        this.userProfileRepository = userProfileRepository;
        this.bookFeatureRepository = bookFeatureRepository;
        this.bookProgressInteraction = bookProgressInteraction;
    }

    /**
     * Updates the user's profile based on their progress with a book. It retrieves the user's profile and the book's features,
     * @param command the command containing the data for updating the book progress.
     */
    public void execute(UpdateBookProgressCommand command){
        BookFeature book = bookFeatureRepository.findById(command.bookId())
                .orElse(null);
        if (book == null) {
            log.warn(
                    "Skipping book progress profile update because book features are not available yet. UserId={}, BookId={}",
                    command.userId(),
                    command.bookId()
            );
            return;
        }

        UserProfile profile = userProfileRepository.findById(command.userId())
                .orElseGet(() -> new UserProfile(command.userId()));
        ProfileAdjustment profileAdjustment = bookProgressInteraction.toAdjustment(
                book,
                command.progress(),
                command.oldPage(),
                command.newPage()
        );

        profile.applyUpdate(profileAdjustment);

        userProfileRepository.save(profile);
    }


}
