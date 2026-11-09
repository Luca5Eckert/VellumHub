package com.vellumhub.catalog_service.module.book.infrastructure.persistence;

import com.vellumhub.catalog_service.module.book.domain.port.BookWriteGate;
import com.vellumhub.catalog_service.module.book.domain.exception.BookWritesFrozenException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * SELECT FOR SHARE holds the fence until the mutation+Outbox transaction commits.
 * An operator UPDATE to frozen=true waits for all in-flight Book transactions;
 * later Book writes abort without committing partial domain state.
 */
@Repository
public class JdbcBookWriteGate implements BookWriteGate {
    private final JdbcTemplate jdbc;
    public JdbcBookWriteGate(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void requireWritable() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Book mutation + Outbox must share a database transaction");
        Boolean frozen = jdbc.queryForObject(
                "SELECT frozen FROM book_write_gate WHERE id = 1 FOR SHARE", Boolean.class);
        if (frozen == null) throw new IllegalStateException("Missing Book cutover fence row");
        if (frozen) throw new BookWritesFrozenException();
    }
}
