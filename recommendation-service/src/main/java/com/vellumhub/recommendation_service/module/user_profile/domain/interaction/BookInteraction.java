package com.vellumhub.recommendation_service.module.user_profile.domain.interaction;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;

public interface BookInteraction
{
    ProfileAdjustment toAdjustment(BookFeature bookFeature);

}
