package com.vellumhub.recommendation_service.module.user_profile.application.use_case;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateBookProgressCommand;
import com.vellumhub.recommendation_service.module.user_profile.domain.interaction.BookInteraction;
import com.vellumhub.recommendation_service.module.user_profile.domain.interaction.progress.BookProgressInteraction;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.UserProfile;
import com.vellumhub.recommendation_service.module.user_profile.domain.port.UserProfileRepository;
import org.springframework.stereotype.Service;

@Service
public class UpdateBookProgressUseCase {

    private final UserProfileRepository userProfileRepository;
    private final BookFeatureRepository bookFeatureRepository;

    public UpdateBookProgressUseCase(UserProfileRepository userProfileRepository, BookFeatureRepository bookFeatureRepository) {
        this.userProfileRepository = userProfileRepository;
        this.bookFeatureRepository = bookFeatureRepository;
    }

    /**
     * Updates the user's profile based on their progress with a book. It retrieves the user's profile and the book's features,
     * @param command the command containing the data for updating the book progress.
     */
    public void execute(UpdateBookProgressCommand command){
        UserProfile profile = userProfileRepository.findById(command.userId())
                .orElseGet(() -> new UserProfile(command.userId()));

        BookFeature book = bookFeatureRepository.findById(command.bookId())
                .orElseThrow(() -> new RuntimeException("Book features not found"));

        ProfileAdjustment profileAdjustment = getProfileAdjustment(
                command.oldPage(),
                command.newPage(),
                command.progress(),
                book
        );

        profile.applyUpdate(profileAdjustment);
    }

    /**
     * Calculates the profile adjustment based on the user's progress with the book.
     * @param oldPages the number of pages the user had read before the update.
     * @param newPages the number of pages the user has read after the update.
     * @param progress the current progress status of the book (e.g., "WANT_TO_READ", "READING", "COMPLETED").
     * @param book the book feature data used to calculate the profile adjustment.
     * @return a ProfileAdjustment object that represents the changes to be applied to the user's profile based on their book progress.
     */
    private ProfileAdjustment getProfileAdjustment(
            int oldPages,
            int newPages,
            String progress,
            BookFeature book
    ) {
        BookInteraction bookInteraction = new BookProgressInteraction(progress, oldPages, newPages);

        return bookInteraction.toAdjustment(book);
    }

}
