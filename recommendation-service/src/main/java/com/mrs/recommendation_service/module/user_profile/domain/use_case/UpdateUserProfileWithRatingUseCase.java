package com.mrs.recommendation_service.module.user_profile.domain.use_case;

import com.mrs.recommendation_service.module.user_profile.domain.command.UpdateUserProfileWithRatingCommand;
import com.mrs.recommendation_service.module.user_profile.domain.model.UserProfile;
import com.mrs.recommendation_service.module.user_profile.domain.port.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateUserProfileWithRatingUseCase {

    private final UserProfileRepository repository;

    @Transactional
    public void execute(UpdateUserProfileWithRatingCommand command) {
        UserProfile profile = repository.findById(command.userId())
                .orElseGet(() -> new UserProfile(command.userId()));

        profile.updateScoreByRating(command);

        repository.save(profile);
    }

}