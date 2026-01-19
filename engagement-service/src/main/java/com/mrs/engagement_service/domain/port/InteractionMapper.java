package com.mrs.engagement_service.domain.port;

import com.mrs.engagement_service.application.dto.GetMediaStatusResponse;
import com.mrs.engagement_service.application.dto.InteractionGetResponse;
import com.mrs.engagement_service.domain.model.EngagementStats;
import com.mrs.engagement_service.domain.model.Interaction;

import java.util.UUID;

public interface InteractionMapper {

    InteractionGetResponse toGetResponse(Interaction interaction);

    GetMediaStatusResponse toMediaStatusResponse(EngagementStats engagementStats, UUID mediaId);

}
