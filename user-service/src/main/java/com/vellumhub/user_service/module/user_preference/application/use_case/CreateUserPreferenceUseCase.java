package com.vellumhub.user_service.module.user_preference.application.use_case;

import com.vellumhub.user_service.module.user.application.exception.UserNotFoundException;
import com.vellumhub.user_service.module.user.domain.UserEntity;
import com.vellumhub.user_service.module.user.domain.port.UserRepository;
import com.vellumhub.user_service.module.user_preference.application.command.CreateUserPreferenceCommand;
import com.vellumhub.user_service.module.user_preference.domain.event.CreateUserPreferenceEvent;
import com.vellumhub.user_service.module.user_preference.domain.model.UserPreference;
import com.vellumhub.user_service.module.user_preference.domain.port.UserPreferenceRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Use case for creating or updating user preference.
 */
@Component
public class CreateUserPreferenceUseCase {

    private final UserPreferenceRepository userPreferenceRepository;
    private final UserRepository userRepository;

    private final KafkaTemplate<String, CreateUserPreferenceEvent> kafkaTemplate;

    public CreateUserPreferenceUseCase(UserPreferenceRepository userPreferenceRepository, UserRepository userRepository, KafkaTemplate<String, CreateUserPreferenceEvent> kafkaTemplate) {
        this.userPreferenceRepository = userPreferenceRepository;
        this.userRepository = userRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Create or update user preference.
     * @param command the command to create or update user preference
     */
    @Transactional
    public void execute(CreateUserPreferenceCommand command) {
        var user = userRepository.findById(command.userId())
                .orElseThrow(UserNotFoundException::new);

        var userPreference = getUserPreference(
                user,
                command.genres(),
                command.about()
        );

        userPreferenceRepository.save(userPreference);

        CreateUserPreferenceEvent createUserPreferenceEvent = new CreateUserPreferenceEvent(
                userPreference.getUser().getId(),
                userPreference.getGenres(),
                userPreference.getAbout()
        );

        kafkaTemplate.send("create_user_preference", createUserPreferenceEvent.userId().toString(), createUserPreferenceEvent);

    }

    /**
     * Get user preference by user id. If user preference not exist, create new user preference with given genres and about.
     * @param user the user.
     * @param genres the genres of user preference
     * @param about the about of user preference
     * @return the user preference
     */
    private UserPreference getUserPreference(UserEntity user, List<String> genres, String about) {
        return userPreferenceRepository.findByUserId(user.getId())
                .orElse(
                        UserPreference.builder()
                                .user(user)
                                .genres(genres)
                                .about(about)
                                .build()
                );
    }


}
