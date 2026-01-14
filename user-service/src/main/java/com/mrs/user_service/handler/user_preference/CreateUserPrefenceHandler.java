package com.mrs.user_service.handler.user_preference;

import com.mrs.user_service.event.CreateUserPrefenceEvent;
import com.mrs.user_service.exception.domain.user_preference.UserPreferenceAlreadyExistException;
import com.mrs.user_service.model.UserPreference;
import com.mrs.user_service.repository.UserPreferenceRepository;
import com.mrs.user_service.repository.UserRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateUserPrefenceHandler {

    private final UserPreferenceRepository userPreferenceRepository;
    private final UserRepository userRepository;

    private final KafkaTemplate<String, CreateUserPrefenceEvent> kafkaTemplate;

    public CreateUserPrefenceHandler(UserPreferenceRepository userPreferenceRepository, UserRepository userRepository, KafkaTemplate<String, CreateUserPrefenceEvent> kafkaTemplate) {
        this.userPreferenceRepository = userPreferenceRepository;
        this.userRepository = userRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public void execute(UserPreference userPreference){
        if(!userRepository.existsById(userPreference.getUserId())) throw new IllegalArgumentException("User not found");

        // Race condition //
        if(userPreferenceRepository.existsByUserId(userPreference.getUserId())) throw new UserPreferenceAlreadyExistException("User already have a preference");

        userPreferenceRepository.save(userPreference);

        CreateUserPrefenceEvent createUserPrefenceEvent = new CreateUserPrefenceEvent(
                userPreference.getUserId(),
                userPreference.getGenres()
        );

        kafkaTemplate.send("create_user_preference", createUserPrefenceEvent.userId().toString(), createUserPrefenceEvent);

    }


}
