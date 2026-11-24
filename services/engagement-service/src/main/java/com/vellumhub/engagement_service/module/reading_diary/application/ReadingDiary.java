package com.vellumhub.engagement_service.module.reading_diary.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReadingDiary {
    private ReadingDiary() {}

    public enum NoteType { NOTE, QUOTE }

    public record Note(UUID id, NoteType type, Integer page, String text, Instant readAt) {}

    public record Entry(String id, String type, Integer page, Integer delta, String text, Instant readAt) {}

    public record Entries(List<Entry> entries, String nextCursor) {}

    public record Cursor(Instant time, String id) {}
}
