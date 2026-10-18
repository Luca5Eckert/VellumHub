package com.vellumhub.user_service.module.auth.domain.port;

/** Serializes identity and account binding operations across service instances. */
public interface ExternalIdentityLock {
    void acquire(String... keys);
}
