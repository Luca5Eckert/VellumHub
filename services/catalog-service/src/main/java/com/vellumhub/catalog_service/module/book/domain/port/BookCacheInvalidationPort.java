package com.vellumhub.catalog_service.module.book.domain.port;

import java.util.UUID;

/**
 * Best-effort read-side invalidation. The transactional writer records the
 * canonical Outbox fact first; implementations schedule invalidation after commit.
 */
public interface BookCacheInvalidationPort {
    void invalidateAfterCommit(UUID bookId);
}
