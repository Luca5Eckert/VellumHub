package com.mrs.catalog_service.module.book.domain.model;

import lombok.Getter;

@Getter
public enum Genre {
    FANTASY(0),
    SCI_FI(1),
    HORROR(2),
    THRILLER_MYSTERY(3),
    ROMANCE(4),

    CLASSICS(5),
    CONTEMPORARY(6),
    HISTORICAL_FICTION(7),

    YOUNG_ADULT(8),
    GRAPHIC_NOVELS(9),

    BIOGRAPHY_MEMOIR(10),
    SELF_HELP(11),
    PHILOSOPHY_RELIGION(12),
    HISTORY_POLITICS(13),
    SCIENCE_TECHNOLOGY(14);

    public final int index;

    Genre(int index) {
        this.index = index;
    }

    public static int total() {
        return values().length;
    }
}