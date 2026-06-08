package com.vellumhub.recommendation_service.module.user_profile.application.use_case;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.user_profile.application.command.ReactionChangedCommand;
import com.vellumhub.recommendation_service.module.user_profile.domain.interaction.reaction.ReactionBookInteraction;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.UserProfile;
import com.vellumhub.recommendation_service.module.user_profile.domain.port.UserProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ReactionChangedUseCase {

    private final UserProfileRepository userProfileRepository;
    private final BookFeatureRepository bookFeatureRepository;

    private final ReactionBookInteraction reactionBookInteraction;

    public ReactionChangedUseCase(UserProfileRepository userProfileRepository, BookFeatureRepository bookFeatureRepository, ReactionBookInteraction reactionBookInteraction) {
        this.userProfileRepository = userProfileRepository;
        this.bookFeatureRepository = bookFeatureRepository;
        this.reactionBookInteraction = reactionBookInteraction;
    }

    /**
     * Handles changes in user reactions (like, dislike, etc.) to books. It retrieves the user's profile and the book's features,
     * calculates the necessary adjustments based on the new reaction, and updates the user's profile accordingly.
     * @param command The command containing details about the user, book, and the new reaction type.
     */
    public void execute(ReactionChangedCommand command){
        BookFeature book = bookFeatureRepository.findById(command.bookId())
                .orElse(null);
        if (book == null) {
            log.warn(
                    "Skipping reaction profile update because book features are not available yet. UserId={}, BookId={}",
                    command.userId(),
                    command.bookId()
            );
            return;
        }

        UserProfile profile = userProfileRepository.findById(command.userId())
                .orElseGet(() -> new UserProfile(command.userId()));
        ProfileAdjustment profileAdjustment = reactionBookInteraction.toAdjustment(
                book,
                command.reactionType()
        );

        profile.applyUpdate(profileAdjustment);

        userProfileRepository.save(profile);
    }


}
