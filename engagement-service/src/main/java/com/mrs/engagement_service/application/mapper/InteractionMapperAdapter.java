package com.mrs.engagement_service.application.mapper;

import com.mrs.engagement_service.application.dto.GetMediaStatusResponse;
import com.mrs.engagement_service.application.dto.InteractionGetResponse;
import com.mrs.engagement_service.domain.model.EngagementStats;
import com.mrs.engagement_service.domain.model.Interaction;
import com.mrs.engagement_service.domain.port.InteractionMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InteractionMapperAdapter implements InteractionMapper {

    @Override
    public InteractionGetResponse toGetResponse(
            Interaction interaction
    ) {
        return new InteractionGetResponse(
                interaction.getId(),
                interaction.getUserId(),
                interaction.getMediaId(),
                interaction.getType(),
                interaction.getInteractionValue(),
                interaction.getTimestamp()
        );
    }

    @Override
    public GetMediaStatusResponse toMediaStatusResponse(EngagementStats engagementStats, UUID mediaId) {
        return new GetMediaStatusResponse(
                mediaId,
                engagementStats.getTotalViews(),
                engagementStats.getTotalLikes(),
                engagementStats.getTotalDislikes(),
                engagementStats.getAverageRating(),
                engagementStats.getTotalRatings(),
                engagementStats.getTotalInteractions(),
                engagementStats.getPopularityScore()
        );
    }

}
