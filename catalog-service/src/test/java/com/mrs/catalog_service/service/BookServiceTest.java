package com.mrs.catalog_service.service;

import com.mrs.catalog_service.module.book.application.dto.CreateBookRequest;
import com.mrs.catalog_service.module.book.application.dto.GetBookResponse;
import com.mrs.catalog_service.module.book.application.dto.PageBook;
import com.mrs.catalog_service.module.book.application.dto.UpdateBookRequest;
import com.mrs.catalog_service.module.book.application.mapper.BookMapper;
import com.mrs.catalog_service.module.book.domain.handler.*;
import com.mrs.catalog_service.module.book.domain.model.Genre;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.service.BookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BookService.
 * Tests cover all service methods with mocked handlers.
 * Follows the pattern: condition_expectedBehavior.
 */
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private CreateBookHandler createMediaHandler;

    @Mock
    private DeleteBookHandler deleteMediaHandler;

    @Mock
    private GetBookHandler getMediaHandler;

    @Mock
    private GetAllBooksHandler getAllMediaHandler;

    @Mock
    private UpdateBookHandler updateMediaHandler;

    @Mock
    private BookMapper bookMapper;

    @InjectMocks
    private BookService bookService;

    @Nested
    @DisplayName("create() method tests")
    class CreateTests {

        @Test
        @DisplayName("whenValidRequest_shouldCreateMedia")
        void whenValidRequest_shouldCreateMedia() {
            // Arrange
            CreateBookRequest request = new CreateBookRequest(
                    "The Matrix",
                    "A computer hacker learns about the true nature of reality",
                    1999,
                    "https://example.com/matrix.jpg",
                    "Wachowski Sisters",
                    "978-0-7653-0000-0",
                    300,
                    "Warner Bros",
                    List.of(Genre.FANTASY, Genre.THRILLER_MYSTERY)
            );

            // Act
            bookService.create(request);

            // Assert
            ArgumentCaptor<Book> mediaCaptor = ArgumentCaptor.forClass(Book.class);
            verify(createMediaHandler, times(1)).handler(mediaCaptor.capture());

            Book capturedMedia = mediaCaptor.getValue();
            assertThat(capturedMedia.getTitle()).isEqualTo("The Matrix");
            assertThat(capturedMedia.getDescription()).isEqualTo("A computer hacker learns about the true nature of reality");
            assertThat(capturedMedia.getReleaseYear()).isEqualTo(1999);
            assertThat(capturedMedia.getAuthor()).isEqualTo("Wachowski Sisters");
            assertThat(capturedMedia.getIsbn()).isEqualTo("978-0-7653-0000-0");
            assertThat(capturedMedia.getPageCount()).isEqualTo(300);
            assertThat(capturedMedia.getPublisher()).isEqualTo("Warner Bros");
            assertThat(capturedMedia.getGenres()).containsExactlyInAnyOrder(Genre.FANTASY, Genre.THRILLER_MYSTERY);
        }

        @Test
        @DisplayName("whenSeriesType_shouldCreateSeries")
        void whenSeriesType_shouldCreateSeries() {
            // Arrange
            CreateBookRequest request = new CreateBookRequest(
                    "Breaking Bad",
                    "A chemistry teacher turns to a life of crime",
                    2008,
                    "https://example.com/bb.jpg",
                    "Vince Gilligan",
                    "978-0-7653-1111-1",
                    250,
                    "AMC Books",
                    List.of(Genre.FANTASY, Genre.THRILLER_MYSTERY)
            );

            // Act
            bookService.create(request);

            // Assert
            ArgumentCaptor<Book> mediaCaptor = ArgumentCaptor.forClass(Book.class);
            verify(createMediaHandler).handler(mediaCaptor.capture());

            Book capturedMedia = mediaCaptor.getValue();
            assertThat(capturedMedia.getAuthor()).isEqualTo("Vince Gilligan");
            assertThat(capturedMedia.getIsbn()).isEqualTo("978-0-7653-1111-1");
        }
    }

    @Nested
    @DisplayName("delete() method tests")
    class DeleteTests {

        @Test
        @DisplayName("whenValidMediaId_shouldDeleteMedia")
        void whenValidMediaId_shouldDeleteMedia() {
            // Arrange
            UUID bookId = UUID.randomUUID();

            // Act
            bookService.delete(bookId);

            // Assert
            verify(deleteMediaHandler, times(1)).execute(bookId);
        }
    }

    @Nested
    @DisplayName("get() method tests")
    class GetTests {

        @Test
        @DisplayName("whenValidMediaId_shouldReturnMediaResponse")
        void whenValidMediaId_shouldReturnMediaResponse() {
            // Arrange
            UUID bookId = UUID.randomUUID();
            Instant now = Instant.now();

            Book book = Book.builder()
                    .id(bookId)
                    .title("The Matrix")
                    .description("A computer hacker learns about the true nature of reality")
                    .releaseYear(1999)
                    .author("Wachowski Sisters")
                    .isbn("978-0-7653-0000-0")
                    .pageCount(300)
                    .publisher("Warner Bros")
                    .coverUrl("https://example.com/matrix.jpg")
                    .genres(List.of(Genre.FANTASY, Genre.THRILLER_MYSTERY))
                    .build();

            GetBookResponse expectedResponse = new GetBookResponse(
                    bookId,
                    "The Matrix",
                    "A computer hacker learns about the true nature of reality",
                    1999,
                    "https://example.com/matrix.jpg",
                    "Wachowski Sisters",
                    "978-0-7653-0000-0",
                    300,
                    "Warner Bros",
                    List.of(Genre.FANTASY, Genre.THRILLER_MYSTERY),
                    now,
                    now
            );

            when(getMediaHandler.execute(bookId)).thenReturn(book);
            when(bookMapper.toGetResponse(book)).thenReturn(expectedResponse);

            // Act
            GetBookResponse result = bookService.get(bookId);

            // Assert
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(result.id()).isEqualTo(bookId);
            assertThat(result.title()).isEqualTo("The Matrix");

            verify(getMediaHandler, times(1)).execute(bookId);
            verify(bookMapper, times(1)).toGetResponse(book);
        }
    }

    @Nested
    @DisplayName("getAll() method tests")
    class GetAllTests {

        @Test
        @DisplayName("whenMediaExists_shouldReturnMediaList")
        void whenMediaExists_shouldReturnMediaList() {
            // Arrange
            int pageNumber = 0;
            int pageSize = 10;
            Instant now = Instant.now();

            UUID mediaId1 = UUID.randomUUID();
            UUID mediaId2 = UUID.randomUUID();

            Book media1 = Book.builder()
                    .id(mediaId1)
                    .title("The Matrix")
                    .author("Wachowski Sisters")
                    .isbn("978-0-7653-0000-0")
                    .pageCount(300)
                    .publisher("Warner Bros")
                    .genres(List.of(Genre.FANTASY))
                    .build();

            Book media2 = Book.builder()
                    .id(mediaId2)
                    .title("Breaking Bad")
                    .author("Vince Gilligan")
                    .isbn("978-0-7653-1111-1")
                    .pageCount(250)
                    .publisher("AMC Books")
                    .genres(List.of(Genre.HORROR))
                    .build();

            Page<Book> bookPage = new PageImpl<>(List.of(media1, media2));

            GetBookResponse response1 = new GetBookResponse(mediaId1, "The Matrix", "Desc 1",
                    1999, "url1", "Wachowski Sisters", "978-0-7653-0000-0", 300, "Warner Bros", List.of(Genre.THRILLER_MYSTERY), now, now);
            GetBookResponse response2 = new GetBookResponse(mediaId2, "Breaking Bad", "Desc 2",
                    2008, "url2", "Vince Gilligan", "978-0-7653-1111-1", 250, "AMC Books", List.of(Genre.HORROR), now, now);

            when(getAllMediaHandler.execute(any(PageBook.class))).thenReturn(bookPage);
            when(bookMapper.toGetResponse(media1)).thenReturn(response1);
            when(bookMapper.toGetResponse(media2)).thenReturn(response2);

            // Act
            List<GetBookResponse> result = bookService.getAll(pageNumber, pageSize);

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).title()).isEqualTo("The Matrix");
            assertThat(result.get(1).title()).isEqualTo("Breaking Bad");

            ArgumentCaptor<PageBook> pageCaptor = ArgumentCaptor.forClass(PageBook.class);
            verify(getAllMediaHandler, times(1)).execute(pageCaptor.capture());

            PageBook capturedPage = pageCaptor.getValue();
            assertThat(capturedPage.pageNumber()).isEqualTo(pageNumber);
            assertThat(capturedPage.pageSize()).isEqualTo(pageSize);
        }

        @Test
        @DisplayName("whenNoMedia_shouldReturnEmptyList")
        void whenNoMedia_shouldReturnEmptyList() {
            // Arrange
            int pageNumber = 0;
            int pageSize = 10;

            Page<Book> emptyPage = new PageImpl<>(List.of());
            when(getAllMediaHandler.execute(any(PageBook.class))).thenReturn(emptyPage);

            // Act
            List<GetBookResponse> result = bookService.getAll(pageNumber, pageSize);

            // Assert
            assertThat(result).isEmpty();
            verify(getAllMediaHandler, times(1)).execute(any(PageBook.class));
            verify(bookMapper, never()).toGetResponse(any(Book.class));
        }
    }

    @Nested
    @DisplayName("update() method tests")
    class UpdateTests {

        @Test
        @DisplayName("whenValidRequest_shouldUpdateMedia")
        void whenValidRequest_shouldUpdateMedia() {
            // Arrange
            UUID bookId = UUID.randomUUID();
            UpdateBookRequest request = new UpdateBookRequest(
                    "Updated Title",
                    "Updated Description",
                    2000,
                    "https://example.com/updated.jpg",
                    "Updated Author",
                    "978-0-7653-9999-9",
                    350,
                    "Updated Publisher",
                    List.of(Genre.FANTASY, Genre.HORROR)
            );

            // Act
            bookService.update(bookId, request);

            // Assert
            verify(updateMediaHandler, times(1)).execute(bookId, request);
        }
    }
}
