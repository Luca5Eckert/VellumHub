package com.vellumhub.user_service.module.user_preference.application.use_case;

import com.vellumhub.user_service.module.user.application.exception.UserNotFoundException;
import com.vellumhub.user_service.module.user.domain.port.UserRepository;
import com.vellumhub.user_service.module.user_preference.application.command.CreateUserPreferenceCommand;
import com.vellumhub.user_service.module.user_preference.domain.model.UserPreference;
import com.vellumhub.user_service.module.user_preference.domain.port.UserPreferenceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Use case for creating or updating user preference.
 */
@Component
public class CreateUserPreferenceUseCase {

    private final UserPreferenceRepository userPreferenceRepository;
    private final UserRepository userRepository;

    private final PreferenceOutbox outbox;

    public CreateUserPreferenceUseCase(UserPreferenceRepository userPreferenceRepository,
                                       UserRepository userRepository, PreferenceOutbox outbox) {
        this.userPreferenceRepository = userPreferenceRepository;
        this.userRepository = userRepository;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<String> findGenres(UUID userId) {
        return userPreferenceRepository.findByUserId(userId)
                .map(preference -> List.copyOf(preference.getGenres()))
                .orElseGet(List::of);
    }

    @Transactional
    public void execute(CreateUserPreferenceCommand command) {
        var user = userRepository.findByIdForPreferenceUpdate(command.userId())
                .orElseThrow(UserNotFoundException::new);

        var preference = userPreferenceRepository.findByUserId(user.getId())
                .orElseGet(() -> UserPreference.builder().user(user).build());
        preference.setGenres(new ArrayList<>(command.genres()));
        if (command.about() != null) {
            preference.setAbout(command.about());
        }
        preference.setRevision(preference.getRevision() + 1);
        userPreferenceRepository.save(preference);
        outbox.enqueue(user.getId(), preference.getRevision(), preference.getGenres(), preference.getAbout());
    }
}
