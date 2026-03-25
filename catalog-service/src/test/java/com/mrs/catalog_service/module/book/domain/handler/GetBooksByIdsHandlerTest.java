package com.mrs.catalog_service.module.book.domain.handler;

import com.mrs.catalog_service.module.book.domain.handler.GetBooksByIdsHandler;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.model.Genre;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetBooksByIdsHandler Unit Tests")
class GetBooksByIdsHandlerTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private GetBooksByIdsHandler getBooksByIdsHandler;

    @Nested
    @DisplayName("Success cases")
    class SuccessCases {

        @Test
        @DisplayName("Should return books when valid IDs are provided")
        void shouldReturnBooksWhenValidIdsProvided() {
            // Given
            UUID bookId1 = UUID.randomUUID();
            UUID bookId2 = UUID.randomUUID();
            List<UUID> bookIds = Arrays.asList(bookId1, bookId2);

            Book book1 = Book.builder()
                    .id(bookId1)
                    .title("The Hobbit")
                    .author("J.R.R. Tolkien")
                    .isbn("978-0-261-10221-4")
                    .genres(Set.of(new Genre("FANTASY")))
                    .build();

            Book book2 = Book.builder()
                    .id(bookId2)
                    .title("1984")
                    .author("George Orwell")
                    .isbn("978-0-452-28423-4")
                    .genres(Set.of(new Genre("SCI-FI")))
                    .build();

            given(bookRepository.findAllById(bookIds)).willReturn(Arrays.asList(book1, book2));

            // When
            List<Book> result = getBooksByIdsHandler.execute(bookIds);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Book::getId).containsExactly(bookId1, bookId2);
            assertThat(result).extracting(Book::getTitle).containsExactly("The Hobbit", "1984");
            verify(bookRepository).findAllById(bookIds);
        }

        @Test
        @DisplayName("Should return single book when one ID is provided")
        void shouldReturnSingleBookWhenOneIdProvided() {
            // Given
            UUID bookId = UUID.randomUUID();
            List<UUID> bookIds = List.of(bookId);

            Book book = Book.builder()
                    .id(bookId)
                    .title("The Hobbit")
                    .author("J.R.R. Tolkien")
                    .isbn("978-0-261-10221-4")
                    .build();

            given(bookRepository.findAllById(bookIds)).willReturn(List.of(book));

            // When
            List<Book> result = getBooksByIdsHandler.execute(bookIds);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getId()).isEqualTo(bookId);
            assertThat(result.getFirst().getTitle()).isEqualTo("The Hobbit");
        }

        @Test
        @DisplayName("Should return empty list when empty IDs list is provided")
        void shouldReturnEmptyListWhenEmptyIdsProvided() {
            // Given
            List<UUID> emptyIds = Collections.emptyList();
            given(bookRepository.findAllById(emptyIds)).willReturn(Collections.emptyList());

            // When
            List<Book> result = getBooksByIdsHandler.execute(emptyIds);

            // Then
            assertThat(result).isEmpty();
            verify(bookRepository).findAllById(emptyIds);
        }

        @Test
        @DisplayName("Should return partial list when some IDs do not exist")
        void shouldReturnPartialListWhenSomeIdsDoNotExist() {
            // Given
            UUID existingId = UUID.randomUUID();
            UUID nonExistingId = UUID.randomUUID();
            List<UUID> bookIds = Arrays.asList(existingId, nonExistingId);

            Book book = Book.builder()
                    .id(existingId)
                    .title("The Hobbit")
                    .author("J.R.R. Tolkien")
                    .isbn("978-0-261-10221-4")
                    .build();

            given(bookRepository.findAllById(bookIds)).willReturn(List.of(book));

            // When
            List<Book> result = getBooksByIdsHandler.execute(bookIds);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getId()).isEqualTo(existingId);
        }
    }
}
