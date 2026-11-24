package com.vellumhub.engagement_service.module.reading_diary.infrastructure;

import com.vellumhub.engagement_service.module.reading_diary.application.ReadingDiary.Cursor;
import com.vellumhub.engagement_service.module.reading_diary.application.ReadingDiary.Entry;
import com.vellumhub.engagement_service.module.reading_diary.application.ReadingDiary.Note;
import com.vellumhub.engagement_service.module.reading_diary.application.ReadingDiary.NoteType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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

    public boolean hasEntries(UUID userId, UUID readingId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM reading_note WHERE user_id=? AND reading_id=?)
                    OR EXISTS (SELECT 1 FROM reading_session_entries WHERE user_id=? AND reading_session_id=?
                               AND COALESCE(read_at, timestamp) IS NOT NULL)
                """, Boolean.class, userId, readingId, userId, readingId));
    }

    public List<UUID> noteReadingIds(UUID userId, UUID after, int limit) {
        return jdbc.queryForList("""
                SELECT DISTINCT reading_id FROM reading_note WHERE user_id=?
                  AND (?::uuid IS NULL OR reading_id > ?::uuid)
                ORDER BY reading_id LIMIT ?
                """, UUID.class, userId, after, after, limit);
    }

    public Note create(UUID userId, UUID readingId, Note note) {
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

    private static Note mapNote(ResultSet row, int index) throws SQLException {
        return new Note(row.getObject("id", UUID.class), NoteType.valueOf(row.getString("type")),
                row.getObject("page", Integer.class), row.getString("note_text"),
                row.getObject("read_at", OffsetDateTime.class).toInstant());
    }
}
