package com.vellumhub.kafka.contracts;

public final class KafkaTypeAliases {

    public static final String CREATE_BOOK_EVENT = "create_book_event";
    public static final String UPDATE_BOOK_EVENT = "update_book_event";
    public static final String DELETE_BOOK_EVENT = "delete_book_event";

    public static final String CREATE_RATING_EVENT = "create_rating_event";
    public static final String REACTION_CHANGED_EVENT = "reaction_changed_event";

    public static final String CREATED_USER_PREFERENCE = "created_user_preference";

    public static final String CREATE_BOOK_PROGRESS_EVENT = "create_book_progress_event";
    public static final String UPDATE_BOOK_PROGRESS_EVENT = "update_book_progress_event";

    private KafkaTypeAliases() {
    }
}
