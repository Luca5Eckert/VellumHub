package com.mrs.catalog_service.module.book.domain.model;

public enum Genre {
    ACTION(0), HORROR(1), SCI_FI(2), DRAMA(3), COMEDY(4), THRILLER(5), ROMANCE(6), FANTASY(7), DOCUMENTARY(8), ANIMATION(9);

    public final int index;
    Genre(int index) { this.index = index; }

    public static int total() { return values().length; }
}