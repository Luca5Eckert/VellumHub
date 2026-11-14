package com.vellumhub.catalog_service.module.book_progress.infrastructure.persistence;

import com.vellumhub.catalog_service.module.book_progress.domain.port.ReadingProgressWriteGate;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.ReadingProgressWritesFrozenException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Locks the fence row through the same mutation + Outbox transaction. */
@Repository
public class JdbcReadingProgressWriteGate implements ReadingProgressWriteGate {
    private final JdbcTemplate jdbc;
    public JdbcReadingProgressWriteGate(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void requireWritable() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Reading Progress mutation and Outbox must be transactional");
        Boolean frozen = jdbc.queryForObject(
                "SELECT frozen FROM reading_progress_write_gate WHERE id=1 FOR SHARE", Boolean.class);
        if (frozen == null) throw new IllegalStateException("Missing Reading Progress fence");
        if (frozen) throw new ReadingProgressWritesFrozenException();
    }
}
