package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.module.book.domain.exception.BookDomainException;
import com.mrs.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.mrs.catalog_service.module.book.domain.handler.UploadBookCoverHandler;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("UploadBookCoverHandler Unit Tests")
class UploadBookCoverHandlerTest {

    @Mock
    private BookCoverStorage bookCoverStorage;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private UploadBookCoverHandler uploadBookCoverHandler;

    private final UUID bookId = UUID.randomUUID();

    @Nested
    @DisplayName("Success cases")
    class SuccessCases {

        @Test
        @DisplayName("Should store file, update book and return coverUrl")
        void shouldStoreFileAndUpdateBook() {
            Book book = mock(Book.class);
            InputStream content = new ByteArrayInputStream(new byte[]{1, 2, 3});
            String coverUrl = "/files/books/" + bookId + "-abc.png";

            given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
            given(bookCoverStorage.store(eq(bookId), any(), eq("cover.png"))).willReturn(coverUrl);

            String result = uploadBookCoverHandler.execute(bookId, content, "cover.png", "image/png");

            assertThat(result).isEqualTo(coverUrl);
            then(book).should().updateCoverUrl(coverUrl);
            then(bookRepository).should().save(book);
        }
    }

    @Nested
    @DisplayName("Validation cases")
    class ValidationCases {

        @Test
        @DisplayName("Should throw BookDomainException when contentType is not an image")
        void shouldThrowWhenNotImage() {
            InputStream content = new ByteArrayInputStream(new byte[]{});

            assertThatThrownBy(() ->
                    uploadBookCoverHandler.execute(bookId, content, "file.pdf", "application/pdf"))
                    .isInstanceOf(BookDomainException.class)
                    .hasMessageContaining("image");
        }

        @Test
        @DisplayName("Should throw BookDomainException when contentType is null")
        void shouldThrowWhenContentTypeNull() {
            InputStream content = new ByteArrayInputStream(new byte[]{});

            assertThatThrownBy(() ->
                    uploadBookCoverHandler.execute(bookId, content, "file.png", null))
                    .isInstanceOf(BookDomainException.class);
        }

        @Test
        @DisplayName("Should throw BookNotFoundException when book does not exist")
        void shouldThrowWhenBookNotFound() {
            InputStream content = new ByteArrayInputStream(new byte[]{1, 2, 3});
            given(bookRepository.findById(bookId)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    uploadBookCoverHandler.execute(bookId, content, "cover.png", "image/png"))
                    .isInstanceOf(BookNotFoundException.class)
                    .hasMessageContaining(bookId.toString());
        }
    }
}
