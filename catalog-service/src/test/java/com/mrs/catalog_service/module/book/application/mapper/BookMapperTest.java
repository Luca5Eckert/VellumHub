package com.mrs.catalog_service.module.book.application.mapper;

import com.mrs.catalog_service.module.book.presentation.dto.GetBookResponse;
import com.mrs.catalog_service.module.book.presentation.dto.Recommendation;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.model.Genre;
import com.mrs.catalog_service.module.book.presentation.mapper.BookMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BookMapper Unit Tests")
class BookMapperTest {

    private BookMapper bookMapper;

    @BeforeEach
    void setUp() {
        bookMapper = new BookMapper();
    }

    @Nested
    @DisplayName("toGetResponse() method tests")
    class ToGetResponseTests {

        @Test
        @DisplayName("Should map Book to GetBookResponse with all fields")
        void shouldMapBookToGetBookResponseWithAllFields() {
            // Given
            UUID bookId = UUID.randomUUID();
            Instant now = Instant.now();

            Book book = Book.builder()
                    .id(bookId)
                    .title("The Hobbit")
                    .description("A fantasy novel about a hobbit's adventure")
                    .releaseYear(1937)
                    .coverUrl("https://example.com/hobbit.jpg")
                    .author("J.R.R. Tolkien")
                    .isbn("978-0-261-10221-4")
                    .pageCount(310)
                    .publisher("George Allen & Unwin")
                    .genres(List.of(Genre.FANTASY, Genre.ROMANCE))
                    .build();

            // Simulating JPA audit fields
            java.lang.reflect.Field createdAtField;
            java.lang.reflect.Field updatedAtField;
            try {
                createdAtField = Book.class.getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                createdAtField.set(book, now);

                updatedAtField = Book.class.getDeclaredField("updatedAt");
                updatedAtField.setAccessible(true);
                updatedAtField.set(book, now);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // When
            GetBookResponse response = bookMapper.toGetResponse(book);

            // Then
            assertThat(response.id()).isEqualTo(bookId);
            assertThat(response.title()).isEqualTo("The Hobbit");
            assertThat(response.description()).isEqualTo("A fantasy novel about a hobbit's adventure");
            assertThat(response.releaseYear()).isEqualTo(1937);
            assertThat(response.coverUrl()).isEqualTo("https://example.com/hobbit.jpg");
            assertThat(response.author()).isEqualTo("J.R.R. Tolkien");
            assertThat(response.isbn()).isEqualTo("978-0-261-10221-4");
            assertThat(response.pageCount()).isEqualTo(310);
            assertThat(response.publisher()).isEqualTo("George Allen & Unwin");
            assertThat(response.genres()).containsExactly(Genre.FANTASY, Genre.ROMANCE);
            assertThat(response.createdAt()).isEqualTo(now);
            assertThat(response.updatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("Should handle null optional fields")
        void shouldHandleNullOptionalFields() {
            // Given
            UUID bookId = UUID.randomUUID();

            Book book = Book.builder()
                    .id(bookId)
                    .title("Test Book")
                    .author("Test Author")
                    .isbn("123-456-789")
                    .pageCount(100)
                    .publisher("Test Publisher")
                    .releaseYear(2023)
                    .description(null)
                    .coverUrl(null)
                    .genres(List.of())
                    .build();

            // When
            GetBookResponse response = bookMapper.toGetResponse(book);

            // Then
            assertThat(response.id()).isEqualTo(bookId);
            assertThat(response.title()).isEqualTo("Test Book");
            assertThat(response.description()).isNull();
            assertThat(response.coverUrl()).isNull();
            assertThat(response.genres()).isEmpty();
        }
    }

    @Nested
    @DisplayName("toFeatureResponse() method tests")
    class ToFeatureResponseTests {

        @Test
        @DisplayName("Should map Book to Recommendation with all fields")
        void shouldMapBookToRecommendationWithAllFields() {
            // Given
            UUID bookId = UUID.randomUUID();
            Instant now = Instant.now();

            Book book = Book.builder()
                    .id(bookId)
                    .title("1984")
                    .description("A dystopian social science fiction novel")
                    .releaseYear(1949)
                    .coverUrl("https://example.com/1984.jpg")
                    .author("George Orwell")
                    .isbn("978-0-452-28423-4")
                    .pageCount(328)
                    .publisher("Secker & Warburg")
                    .genres(List.of(Genre.SCI_FI, Genre.THRILLER_MYSTERY))
                    .build();

            // Simulating JPA audit fields
            try {
                java.lang.reflect.Field createdAtField = Book.class.getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                createdAtField.set(book, now);

                java.lang.reflect.Field updatedAtField = Book.class.getDeclaredField("updatedAt");
                updatedAtField.setAccessible(true);
                updatedAtField.set(book, now);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // When
            Recommendation recommendation = bookMapper.toFeatureResponse(book);

            // Then
            assertThat(recommendation.id()).isEqualTo(bookId);
            assertThat(recommendation.title()).isEqualTo("1984");
            assertThat(recommendation.description()).isEqualTo("A dystopian social science fiction novel");
            assertThat(recommendation.releaseYear()).isEqualTo(1949);
            assertThat(recommendation.coverUrl()).isEqualTo("https://example.com/1984.jpg");
            assertThat(recommendation.genres()).containsExactly(Genre.SCI_FI, Genre.THRILLER_MYSTERY);
            assertThat(recommendation.createdAt()).isEqualTo(now);
            assertThat(recommendation.updatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("Should not include author, isbn, pageCount, publisher in Recommendation")
        void shouldNotIncludeExcludedFieldsInRecommendation() {
            // Given
            UUID bookId = UUID.randomUUID();

            Book book = Book.builder()
                    .id(bookId)
                    .title("Test Book")
                    .description("Test Description")
                    .releaseYear(2023)
                    .coverUrl("https://example.com/test.jpg")
                    .author("Test Author")
                    .isbn("123-456-789")
                    .pageCount(100)
                    .publisher("Test Publisher")
                    .genres(List.of(Genre.FANTASY))
                    .build();

            // When
            Recommendation recommendation = bookMapper.toFeatureResponse(book);

            // Then
            // Recommendation record does not have author, isbn, pageCount, publisher
            // Just verify the fields that should be present
            assertThat(recommendation.id()).isEqualTo(bookId);
            assertThat(recommendation.title()).isEqualTo("Test Book");
            assertThat(recommendation.description()).isEqualTo("Test Description");
            assertThat(recommendation.releaseYear()).isEqualTo(2023);
            assertThat(recommendation.coverUrl()).isEqualTo("https://example.com/test.jpg");
            assertThat(recommendation.genres()).containsExactly(Genre.FANTASY);
        }
    }
}
