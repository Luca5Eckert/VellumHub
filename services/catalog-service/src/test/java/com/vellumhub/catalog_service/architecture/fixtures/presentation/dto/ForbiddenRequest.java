package com.vellumhub.catalog_service.architecture.fixtures.presentation.dto;

/** Negative fixture: an HTTP DTO that an application handler must never accept. */
public record ForbiddenRequest(String value) { }
