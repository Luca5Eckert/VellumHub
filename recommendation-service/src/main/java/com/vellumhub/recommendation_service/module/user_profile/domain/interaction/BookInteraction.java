package com.vellumhub.recommendation_service.module.user_profile.domain.interaction;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;

import java.util.UUID;

public interface BookInteraction
{
    public ProfileAdjustment toAdjustment(BookFeature bookFeature);

}
