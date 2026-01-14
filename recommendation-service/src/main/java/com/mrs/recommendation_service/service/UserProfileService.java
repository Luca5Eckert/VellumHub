package com.mrs.recommendation_service.service;

import com.mrs.recommendation_service.event.InteractionEvent;
import com.mrs.recommendation_service.exception.domain.media_feature.MediaFeatureNotFoundException;
import com.mrs.recommendation_service.model.MediaFeature;
import com.mrs.recommendation_service.model.UserProfile;
import com.mrs.recommendation_service.repository.MediaFeatureRepository;
import com.mrs.recommendation_service.repository.UserProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final MediaFeatureRepository mediaFeatureRepository;

    public UserProfileService(UserProfileRepository userProfileRepository, MediaFeatureRepository mediaFeatureRepository) {
        this.userProfileRepository = userProfileRepository;
        this.mediaFeatureRepository = mediaFeatureRepository;
    }

    @Transactional
    public void update(InteractionEvent interactionEvent) {
        log.debug("Iniciando atualização de perfil: User={}, Mídia={}, Tipo={}",
                interactionEvent.userId(), interactionEvent.mediaId(), interactionEvent.interactionType());

        MediaFeature mediaInteraction = mediaFeatureRepository.findById(interactionEvent.mediaId())
                .orElseThrow(() -> new MediaFeatureNotFoundException(interactionEvent.mediaId().toString()));

        UserProfile userProfile = userProfileRepository.findById(interactionEvent.userId())
                .orElse(new UserProfile(interactionEvent.userId()));

        userProfile.processInteraction(
                mediaInteraction,
                interactionEvent.interactionType(),
                interactionEvent.interactionValue()
        );

        userProfileRepository.save(userProfile);

        log.info("Perfil do usuário {} atualizado com sucesso.", interactionEvent.userId());
    }
}