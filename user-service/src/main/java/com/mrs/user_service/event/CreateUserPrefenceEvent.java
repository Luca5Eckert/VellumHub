package com.mrs.user_service.event;

import com.mrs.user_service.model.Genre;

import java.util.List;
import java.util.UUID;

public record CreateUserPrefenceEvent(
        UUID userId,
        List<Genre> genres
) {
}
