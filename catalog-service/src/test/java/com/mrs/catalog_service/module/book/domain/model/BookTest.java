package com.mrs.catalog_service.module.book.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Book Domain Model Unit Tests")
class BookTest {

    private Book book;

    @BeforeEach
    void setUp() {
        book = Book.builder()
                .title("Original Title")
                .description("Original Description")
                .coverUrl("https://example.com/original.jpg")
                .releaseYear(2000)
                .author("Original Author")
                .isbn("111-222-333")
                .pageCount(200)
                .publisher("Original Publisher")
                .genres(List.of(Genre.FANTASY))
                .build();
    }

    @Nested
    @DisplayName("update() - full update")
    class FullUpdate {

        @Test
        @DisplayName("Should update all fields when all values are provided and valid")
        void shouldUpdateAllFields() {
            List<Genre> newGenres = List.of(Genre.SCI_FI, Genre.HORROR);

            book.update("New Title", "New Desc", "https://new.url/cover.jpg",
                    2023, "New Author", "999-888-777", 350, "New Publisher", newGenres);

            assertThat(book.getTitle()).isEqualTo("New Title");
            assertThat(book.getDescription()).isEqualTo("New Desc");
            assertThat(book.getCoverUrl()).isEqualTo("https://new.url/cover.jpg");
            assertThat(book.getReleaseYear()).isEqualTo(2023);
            assertThat(book.getAuthor()).isEqualTo("New Author");
            assertThat(book.getIsbn()).isEqualTo("999-888-777");
            assertThat(book.getPageCount()).isEqualTo(350);
            assertThat(book.getPublisher()).isEqualTo("New Publisher");
            assertThat(book.getGenres()).containsExactlyInAnyOrder(Genre.SCI_FI, Genre.HORROR);
        }
    }

    @Nested
    @DisplayName("update() - null values keep existing fields")
    class NullValuesKeepExisting {

        @Test
        @DisplayName("Should keep existing title when null is passed")
        void shouldKeepTitleWhenNull() {
            book.update(null, null, null, null, null, null, null, null, null);

            assertThat(book.getTitle()).isEqualTo("Original Title");
            assertThat(book.getDescription()).isEqualTo("Original Description");
            assertThat(book.getAuthor()).isEqualTo("Original Author");
            assertThat(book.getIsbn()).isEqualTo("111-222-333");
        }

        @Test
        @DisplayName("Should keep existing genres when null genres are passed")
        void shouldKeepGenresWhenNull() {
            book.update(null, null, null, null, null, null, null, null, null);

            assertThat(book.getGenres()).containsExactly(Genre.FANTASY);
        }

        @Test
        @DisplayName("Should keep existing releaseYear when null is passed")
        void shouldKeepReleaseYearWhenNull() {
            book.update(null, null, null, null, null, null, null, null, null);

            assertThat(book.getReleaseYear()).isEqualTo(2000);
        }

        @Test
        @DisplayName("Should keep existing pageCount when null is passed")
        void shouldKeepPageCountWhenNull() {
            book.update(null, null, null, null, null, null, null, null, null);

            assertThat(book.getPageCount()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("update() - blank strings keep existing fields")
    class BlankStringsKeepExisting {

        @Test
        @DisplayName("Should keep existing title when blank string is passed")
        void shouldKeepTitleWhenBlank() {
            book.update("  ", null, null, null, null, null, null, null, null);

            assertThat(book.getTitle()).isEqualTo("Original Title");
        }

        @Test
        @DisplayName("Should keep existing description when blank string is passed")
        void shouldKeepDescriptionWhenBlank() {
            book.update(null, "", null, null, null, null, null, null, null);

            assertThat(book.getDescription()).isEqualTo("Original Description");
        }

        @Test
        @DisplayName("Should keep existing author when blank string is passed")
        void shouldKeepAuthorWhenBlank() {
            book.update(null, null, null, null, "   ", null, null, null, null);

            assertThat(book.getAuthor()).isEqualTo("Original Author");
        }
    }

    @Nested
    @DisplayName("update() - zero/negative values keep existing fields")
    class ZeroValuesKeepExisting {

        @Test
        @DisplayName("Should keep existing releaseYear when zero is passed")
        void shouldKeepReleaseYearWhenZero() {
            book.update(null, null, null, 0, null, null, null, null, null);

            assertThat(book.getReleaseYear()).isEqualTo(2000);
        }

        @Test
        @DisplayName("Should keep existing pageCount when zero is passed")
        void shouldKeepPageCountWhenZero() {
            book.update(null, null, null, null, null, null, 0, null, null);

            assertThat(book.getPageCount()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("update() - partial update")
    class PartialUpdate {

        @Test
        @DisplayName("Should update only provided fields and keep others unchanged")
        void shouldUpdateOnlyProvidedFields() {
            book.update("Only Title Updated", null, null, null, null, null, null, null, null);

            assertThat(book.getTitle()).isEqualTo("Only Title Updated");
            assertThat(book.getDescription()).isEqualTo("Original Description");
            assertThat(book.getAuthor()).isEqualTo("Original Author");
            assertThat(book.getIsbn()).isEqualTo("111-222-333");
            assertThat(book.getReleaseYear()).isEqualTo(2000);
        }
    }
}
