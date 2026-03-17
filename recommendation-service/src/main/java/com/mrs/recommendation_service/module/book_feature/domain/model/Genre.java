package com.mrs.recommendation_service.module.book_feature.domain.model;

import lombok.Getter;

@Getter
public enum Genre {

    FANTASY(0, "fantasy"),
    SCI_FI(1, "science fiction"),
    HORROR(2, "horror"),
    THRILLER_MYSTERY(3, "thriller mystery"),
    ROMANCE(4, "romance"),

    CLASSICS(5, "classic literature"),
    CONTEMPORARY(6, "contemporary fiction"),
    HISTORICAL_FICTION(7, "historical fiction"),

    YOUNG_ADULT(8, "young adult"),
    GRAPHIC_NOVELS(9, "graphic novel"),

    BIOGRAPHY_MEMOIR(10, "biography memoir"),
    SELF_HELP(11, "self help"),
    PHILOSOPHY_RELIGION(12, "philosophy religion"),
    HISTORY_POLITICS(13, "history politics"),
    SCIENCE_TECHNOLOGY(14, "science technology");

    private final int index;
    private final String semanticLabel;

    Genre(int index, String semanticLabel) {
        this.index = index;
        this.semanticLabel = semanticLabel;
    }

    public static int total() {
        return values().length;
    }
}