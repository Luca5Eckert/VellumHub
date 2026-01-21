package com.mrs.user_service.module.user_preference.domain.event;


import com.mrs.user_service.module.user.domain.Genre;

import java.util.List;
import java.util.UUID;

public record CreateUserPrefenceEvent(
        UUID userId,
        List<Genre> genres
) {
}
