package com.vellumhub.user_service.module.auth.domain.model;

import java.util.UUID;

public record ExternalIdentity(String issuer, String subject, UUID userId) {
}
