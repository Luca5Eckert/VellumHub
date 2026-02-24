package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.module.book.application.dto.BookCoverResponse;
import com.mrs.catalog_service.module.book.domain.handler.GetBookCoversBulkHandler;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.port.BookCoverStorage;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetBookCoversBulkHandler Unit Tests")
class GetBookCoversBulkHandlerTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookCoverStorage bookCoverStorage;

    @InjectMocks
    private GetBookCoversBulkHandler getBookCoversBulkHandler;

    @Nested
    @DisplayName("Success cases")
    class SuccessCases {

        @Test
        @DisplayName("Should return covers for multiple books")
        void shouldReturnCoversForMultipleBooks() {
            UUID bookId1 = UUID.randomUUID();
            UUID bookId2 = UUID.randomUUID();
            byte[] imageBytes1 = new byte[]{1, 2, 3};
            byte[] imageBytes2 = new byte[]{4, 5, 6};

            Book book1 = mock(Book.class);
            Book book2 = mock(Book.class);
            Resource resource1 = new ByteArrayResource(imageBytes1) {
                @Override
                public String getFilename() {
                    return "cover1.png";
                }
            };
            Resource resource2 = new ByteArrayResource(imageBytes2) {
                @Override
                public String getFilename() {
                    return "cover2.jpg";
                }
            };

            given(book1.getId()).willReturn(bookId1);
            given(book1.getCoverUrl()).willReturn("/files/books/cover1.png");
            given(book2.getId()).willReturn(bookId2);
            given(book2.getCoverUrl()).willReturn("/files/books/cover2.jpg");
            given(bookRepository.findAllById(Arrays.asList(bookId1, bookId2)))
                    .willReturn(Arrays.asList(book1, book2));
            given(bookCoverStorage.load("cover1.png")).willReturn(Optional.of(resource1));
            given(bookCoverStorage.load("cover2.jpg")).willReturn(Optional.of(resource2));

            List<BookCoverResponse> results = getBookCoversBulkHandler.execute(Arrays.asList(bookId1, bookId2));

            assertThat(results).hasSize(2);
            assertThat(results.get(0).bookId()).isEqualTo(bookId1);
            assertThat(results.get(0).coverData()).isEqualTo(Base64.getEncoder().encodeToString(imageBytes1));
            assertThat(results.get(0).contentType()).isEqualTo("image/png");
            assertThat(results.get(1).bookId()).isEqualTo(bookId2);
            assertThat(results.get(1).coverData()).isEqualTo(Base64.getEncoder().encodeToString(imageBytes2));
            assertThat(results.get(1).contentType()).isEqualTo("image/jpeg");
        }

        @Test
        @DisplayName("Should return empty response for book without cover URL")
        void shouldReturnEmptyResponseForBookWithoutCoverUrl() {
            UUID bookId = UUID.randomUUID();
            Book book = mock(Book.class);

            given(book.getId()).willReturn(bookId);
            given(book.getCoverUrl()).willReturn(null);
            given(bookRepository.findAllById(List.of(bookId))).willReturn(List.of(book));

            List<BookCoverResponse> results = getBookCoversBulkHandler.execute(List.of(bookId));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).bookId()).isEqualTo(bookId);
            assertThat(results.get(0).coverData()).isNull();
            assertThat(results.get(0).contentType()).isNull();
        }

        @Test
        @DisplayName("Should return empty response when cover file not found")
        void shouldReturnEmptyResponseWhenCoverFileNotFound() {
            UUID bookId = UUID.randomUUID();
            Book book = mock(Book.class);

            given(book.getId()).willReturn(bookId);
            given(book.getCoverUrl()).willReturn("/files/books/missing.png");
            given(bookRepository.findAllById(List.of(bookId))).willReturn(List.of(book));
            given(bookCoverStorage.load("missing.png")).willReturn(Optional.empty());

            List<BookCoverResponse> results = getBookCoversBulkHandler.execute(List.of(bookId));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).bookId()).isEqualTo(bookId);
            assertThat(results.get(0).coverData()).isNull();
            assertThat(results.get(0).contentType()).isNull();
        }

        @Test
        @DisplayName("Should return empty list for empty input")
        void shouldReturnEmptyListForEmptyInput() {
            given(bookRepository.findAllById(List.of())).willReturn(List.of());

            List<BookCoverResponse> results = getBookCoversBulkHandler.execute(List.of());

            assertThat(results).isEmpty();
        }
    }
}
