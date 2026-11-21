package com.vellumhub.user_service.module.user_preference.application.use_case;

import com.vellumhub.user_service.module.user.application.exception.UserNotFoundException;
import com.vellumhub.user_service.module.user.domain.port.UserRepository;
import com.vellumhub.user_service.module.user_preference.application.command.CreateUserPreferenceCommand;
import com.vellumhub.user_service.module.user_preference.domain.model.UserPreference;
import com.vellumhub.user_service.module.user_preference.domain.port.UserPreferenceRepository;
import org.springframework.beans.factory.annotation.Value;
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
    private final boolean avroEnabled;

    public CreateUserPreferenceUseCase(UserPreferenceRepository userPreferenceRepository,
                                       UserRepository userRepository, PreferenceOutbox outbox,
                                       @Value("${app.preference-outbox.avro-enabled:false}") boolean avroEnabled) {
        this.userPreferenceRepository = userPreferenceRepository;
        this.userRepository = userRepository;
        this.outbox = outbox;
        this.avroEnabled = avroEnabled;
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
        if (avroEnabled) {
            preference.setAvroRevision(preference.getAvroRevision() == null ? 0 : preference.getAvroRevision() + 1);
        } else if (preference.getAvroRevision() != null) {
            throw new IllegalStateException("A migrated preference cannot be published as JSON");
        }
        preference.setGenres(new ArrayList<>(command.genres()));
        if (command.about() != null) {
            preference.setAbout(command.about());
        }
        preference.setRevision(preference.getRevision() + 1);
        userPreferenceRepository.save(preference);
        outbox.enqueue(user.getId(), preference.getRevision(), preference.getAvroRevision(), preference.getGenres(), preference.getAbout());
    }
}
