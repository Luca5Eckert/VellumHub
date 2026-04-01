package com.vellumhub.engagement_service.module.interaction.application.use_case;

import com.vellumhub.engagement_service.module.interaction.application.query.GetAllInteractionByUserQuery;
import com.vellumhub.engagement_service.module.interaction.domain.model.Interaction;
import com.vellumhub.engagement_service.module.interaction.domain.port.InteractionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetAllInteractionByUserUseCase {

    private final InteractionRepository interactionRepository;

    public GetAllInteractionByUserUseCase(InteractionRepository interactionRepository) {
        this.interactionRepository = interactionRepository;
    }

    @Transactional(readOnly = true)
    public List<Interaction> execute(GetAllInteractionByUserQuery query){
        return interactionRepository.findAllByUserId(query.userId());
    }

}
