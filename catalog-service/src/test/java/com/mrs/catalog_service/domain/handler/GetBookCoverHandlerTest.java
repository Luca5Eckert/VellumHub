package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.module.book.domain.exception.BookDomainException;
import com.mrs.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.mrs.catalog_service.module.book.domain.handler.GetBookCoverHandler;
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
import org.springframework.core.io.Resource;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetBookCoverHandler Unit Tests")
class GetBookCoverHandlerTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookCoverStorage bookCoverStorage;

    @InjectMocks
    private GetBookCoverHandler getBookCoverHandler;

    private final UUID bookId = UUID.randomUUID();

    @Nested
    @DisplayName("Success cases")
    class SuccessCases {

        @Test
        @DisplayName("Should return cover Resource when book has cover")
        void shouldReturnCoverResourceWhenBookHasCover() {
            Book book = mock(Book.class);
            Resource expectedResource = mock(Resource.class);
            String coverUrl = "/files/books/cover.png";

            given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
            given(book.getCoverUrl()).willReturn(coverUrl);
            given(bookCoverStorage.load("cover.png")).willReturn(Optional.of(expectedResource));

            Resource result = getBookCoverHandler.execute(bookId);

            assertThat(result).isEqualTo(expectedResource);
        }

        @Test
        @DisplayName("Should extract filename correctly from cover URL")
        void shouldExtractFilenameCorrectly() {
            Book book = mock(Book.class);
            Resource expectedResource = mock(Resource.class);
            String coverUrl = "/files/books/abc-123-456.jpg";

            given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
            given(book.getCoverUrl()).willReturn(coverUrl);
            given(bookCoverStorage.load("abc-123-456.jpg")).willReturn(Optional.of(expectedResource));

            Resource result = getBookCoverHandler.execute(bookId);

            assertThat(result).isEqualTo(expectedResource);
        }
    }

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("Should throw BookNotFoundException when book does not exist")
        void shouldThrowWhenBookNotFound() {
            given(bookRepository.findById(bookId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> getBookCoverHandler.execute(bookId))
                    .isInstanceOf(BookNotFoundException.class)
                    .hasMessageContaining(bookId.toString());
        }

        @Test
        @DisplayName("Should throw BookDomainException when book has no cover URL")
        void shouldThrowWhenNoCoverUrl() {
            Book book = mock(Book.class);

            given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
            given(book.getCoverUrl()).willReturn(null);

            assertThatThrownBy(() -> getBookCoverHandler.execute(bookId))
                    .isInstanceOf(BookDomainException.class)
                    .hasMessageContaining("does not have a cover image");
        }

        @Test
        @DisplayName("Should throw BookDomainException when cover URL is blank")
        void shouldThrowWhenCoverUrlIsBlank() {
            Book book = mock(Book.class);

            given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
            given(book.getCoverUrl()).willReturn("   ");

            assertThatThrownBy(() -> getBookCoverHandler.execute(bookId))
                    .isInstanceOf(BookDomainException.class)
                    .hasMessageContaining("does not have a cover image");
        }

        @Test
        @DisplayName("Should throw BookDomainException when cover file not found")
        void shouldThrowWhenCoverFileNotFound() {
            Book book = mock(Book.class);
            String coverUrl = "/files/books/missing.png";

            given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
            given(book.getCoverUrl()).willReturn(coverUrl);
            given(bookCoverStorage.load("missing.png")).willReturn(Optional.empty());

            assertThatThrownBy(() -> getBookCoverHandler.execute(bookId))
                    .isInstanceOf(BookDomainException.class)
                    .hasMessageContaining("Cover file not found");
        }
    }
}
