package com.vellumhub.kafka.contracts.user;

import java.util.List;

/** Catalog's onboarding taxonomy, also used by the preference event. */
public final class CatalogGenreNames {
    public static final List<String> ONBOARDING = List.of(
            "Science Fiction", "Fantasy", "Horror", "Romance", "Thriller",
            "Biography", "History", "Self-Help", "Dystopian", "Adventure");

    private CatalogGenreNames() {}
}
