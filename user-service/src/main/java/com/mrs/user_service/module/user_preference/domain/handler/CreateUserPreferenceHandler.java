package com.mrs.user_service.module.user_preference.domain.handler;

import com.mrs.user_service.module.user_preference.domain.UserPreference;
import com.mrs.user_service.module.user_preference.domain.event.CreateUserPreferenceEvent;
import com.mrs.user_service.module.user_preference.domain.exception.UserPreferenceAlreadyExistDomainException;
import com.mrs.user_service.module.user_preference.domain.port.UserPreferenceRepository;
import com.mrs.user_service.module.user.domain.port.UserRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateUserPreferenceHandler {

    private final UserPreferenceRepository userPreferenceRepository;
    private final UserRepository userRepository;

    private final KafkaTemplate<String, CreateUserPreferenceEvent> kafkaTemplate;

    public CreateUserPreferenceHandler(UserPreferenceRepository userPreferenceRepository, UserRepository userRepository, KafkaTemplate<String, CreateUserPreferenceEvent> kafkaTemplate) {
        this.userPreferenceRepository = userPreferenceRepository;
        this.userRepository = userRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public void execute(UserPreference userPreference){
        if(!userRepository.existsById(userPreference.getUserId())) throw new IllegalArgumentException("User not found");

        // Race condition //
        if(userPreferenceRepository.existsByUserId(userPreference.getUserId())) throw new UserPreferenceAlreadyExistDomainException("User already have a preference");

        userPreferenceRepository.save(userPreference);

        CreateUserPreferenceEvent createUserPreferenceEvent = new CreateUserPreferenceEvent(
                userPreference.getUserId(),
                userPreference.getGenres()
        );

        kafkaTemplate.send("create_user_preference", createUserPreferenceEvent.userId().toString(), createUserPreferenceEvent);

    }


}
