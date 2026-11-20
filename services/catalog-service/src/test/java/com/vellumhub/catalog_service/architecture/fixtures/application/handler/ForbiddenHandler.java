package com.vellumhub.catalog_service.architecture.fixtures.application.handler;

import com.vellumhub.catalog_service.architecture.fixtures.presentation.dto.ForbiddenRequest;

/** Negative fixture: a use-case handler coupled to a presentation DTO (AFF-STR-001). */
public class ForbiddenHandler {
    public String execute(ForbiddenRequest request) {
        return request.value();
    }
}
