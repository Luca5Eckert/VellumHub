package com.mrs.recommendation_service.exception;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record ApiResponseError(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp,
        List<String> details
) {

}
