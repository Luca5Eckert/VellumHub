package com.vellumhub.engagement_service.module.reading_diary.infrastructure;

import com.vellumhub.engagement_service.module.reading_diary.application.ReadingDiary.Cursor;
import com.vellumhub.engagement_service.module.reading_diary.application.ReadingDiary.Entry;
import com.vellumhub.engagement_service.module.reading_diary.application.ReadingDiary.Note;
import com.vellumhub.engagement_service.module.reading_diary.application.ReadingDiary.NoteType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReadingDiaryStore {
    private final JdbcTemplate jdbc;

    public ReadingDiaryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Entry> entries(UUID userId, UUID readingId, Cursor cursor, int limit) {
        return jdbc.query("""
                WITH timeline AS (
                    SELECT 'p:' || id AS id, type, page_read AS page, page_delta AS delta,
                           NULL::text AS note_text, COALESCE(read_at, timestamp) AS entry_time
                    FROM reading_session_entries
                    WHERE user_id = ? AND reading_session_id = ? AND COALESCE(read_at, timestamp) IS NOT NULL
                    UNION ALL
                    SELECT 'n:' || id, type, page, NULL::integer, note_text, read_at
                    FROM reading_note WHERE user_id = ? AND reading_id = ?
                )
                SELECT * FROM timeline
                WHERE ?::timestamptz IS NULL OR (entry_time, id) < (?::timestamptz, ?)
                ORDER BY entry_time DESC, id DESC LIMIT ?
                """, (row, index) -> new Entry(row.getString("id"), row.getString("type"),
                row.getObject("page", Integer.class), row.getObject("delta", Integer.class),
                row.getString("note_text"), row.getObject("entry_time", OffsetDateTime.class).toInstant()),
                userId, readingId, userId, readingId,
                cursor == null ? null : cursor.time().atOffset(ZoneOffset.UTC),
                cursor == null ? null : cursor.time().atOffset(ZoneOffset.UTC),
                cursor == null ? null : cursor.id(), limit);
    }

    @Transactional
    public Note create(UUID userId, UUID readingId, Note note) {
        if (lockAndIsDeleted(readingId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reading not found");
        }
        return jdbc.queryForObject("""
                INSERT INTO reading_note(id, user_id, reading_id, type, page, note_text, read_at)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING *
                """, ReadingDiaryStore::mapNote, note.id(), userId, readingId, note.type().name(), note.page(),
                note.text(), note.readAt().atOffset(ZoneOffset.UTC));
    }

    public Optional<Note> update(UUID userId, UUID readingId, Note note) {
        return jdbc.query("""
                UPDATE reading_note SET type=?, page=?, note_text=?
                WHERE id=? AND user_id=? AND reading_id=? RETURNING *
                """, ReadingDiaryStore::mapNote, note.type().name(), note.page(), note.text(),
                note.id(), userId, readingId).stream().findFirst();
    }

    public boolean delete(UUID userId, UUID readingId, UUID noteId) {
        return jdbc.update("DELETE FROM reading_note WHERE id=? AND user_id=? AND reading_id=?",
                noteId, userId, readingId) == 1;
    }

    // Serialize projection and note writes with purge while their transactions are active.
    public boolean lockAndIsDeleted(UUID readingId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))",
                (RowCallbackHandler) row -> {}, readingId);
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM deleted_reading_diary WHERE reading_id=?)",
                Boolean.class, readingId));
    }

    @Transactional
    public void purge(UUID userId, UUID readingId) {
        lockAndIsDeleted(readingId);
        jdbc.update("INSERT INTO deleted_reading_diary(reading_id) VALUES (?) ON CONFLICT DO NOTHING", readingId);
        jdbc.update("DELETE FROM reading_note WHERE user_id=? AND reading_id=?", userId, readingId);
        jdbc.update("DELETE FROM reading_session_entries WHERE user_id=? AND reading_session_id=?", userId, readingId);
    }

    private static Note mapNote(ResultSet row, int index) throws SQLException {
        return new Note(row.getObject("id", UUID.class), NoteType.valueOf(row.getString("type")),
                row.getObject("page", Integer.class), row.getString("note_text"),
                row.getObject("read_at", OffsetDateTime.class).toInstant());
    }
}
