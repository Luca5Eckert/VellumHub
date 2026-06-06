package com.vellumhub.engagement_service.module.reaction.application.use_case;

import com.vellumhub.engagement_service.module.reaction.application.query.GetAllReactionByUserQuery;
import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.domain.port.ReactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetAllReactionByUserUseCase {

    private final ReactionRepository reactionRepository;

    public GetAllReactionByUserUseCase(ReactionRepository reactionRepository) {
        this.reactionRepository = reactionRepository;
    }

    @Transactional(readOnly = true)
    public List<Reaction> execute(GetAllReactionByUserQuery query){
        return reactionRepository.findAllByUserId(query.userId());
    }

}
