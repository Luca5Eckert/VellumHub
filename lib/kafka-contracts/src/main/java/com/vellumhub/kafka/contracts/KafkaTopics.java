package com.vellumhub.kafka.contracts;

public final class KafkaTopics {

    public static final String CREATED_BOOK = "created-book";
    public static final String UPDATED_BOOK = "updated-book";
    public static final String DELETED_BOOK = "deleted-book";

    public static final String CREATED_RATING = "created-rating";
    public static final String USER_REACTION_CHANGED = "user-reaction-changed";

    public static final String CREATED_USER_PREFERENCE = "created-user-preference";

    public static final String CREATED_READING_PROGRESS = "created-reading-progress";
    public static final String UPDATED_READING_PROGRESS = "updated-reading-progress";

    public static final String DLT_PATTERN = ".*-dlt";
    public static final String DLT_SUFFIX = "-dlt";

    private KafkaTopics() {
    }
}
