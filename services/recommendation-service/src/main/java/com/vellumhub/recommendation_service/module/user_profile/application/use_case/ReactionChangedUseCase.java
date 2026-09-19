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

    public ReactionChangedUseCase(
            UserProfileRepository userProfileRepository,
            BookFeatureRepository bookFeatureRepository,
            ReactionBookInteraction reactionBookInteraction
    ) {
        this.userProfileRepository = userProfileRepository;
        this.bookFeatureRepository = bookFeatureRepository;
        this.reactionBookInteraction = reactionBookInteraction;
    }

    /**
     * Applies only the semantic delta represented by a reaction lifecycle transition.
     *
     * <p>A creation has no previous reaction and therefore applies the complete new weight.
     * An update applies {@code weight(new) - weight(old)}. Same-value updates yield zero drift.</p>
     */
    public void execute(ReactionChangedCommand command) {
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
                command.oldReactionType(),
                command.newReactionType()
        );

        profile.applyUpdate(profileAdjustment);
        userProfileRepository.save(profile);
    }
}
