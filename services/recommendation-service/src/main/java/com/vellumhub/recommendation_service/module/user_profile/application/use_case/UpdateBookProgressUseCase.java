package com.vellumhub.recommendation_service.module.user_profile.application.use_case;

import com.vellumhub.recommendation_service.module.book_feature.domain.exception.BookFeatureNotFoundException;
import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateBookProgressCommand;
import com.vellumhub.recommendation_service.module.user_profile.domain.interaction.progress.BookProgressInteraction;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.UserProfile;
import com.vellumhub.recommendation_service.module.user_profile.domain.port.UserProfileRepository;
import org.springframework.stereotype.Service;

@Service
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
        UserProfile profile = userProfileRepository.findById(command.userId())
                .orElseGet(() -> new UserProfile(command.userId()));

        BookFeature book = bookFeatureRepository.findById(command.bookId())
                .orElseThrow(() -> new BookFeatureNotFoundException("Book features not found"));

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
