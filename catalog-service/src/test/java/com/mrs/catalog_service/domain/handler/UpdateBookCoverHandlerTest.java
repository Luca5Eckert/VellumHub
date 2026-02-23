package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.mrs.catalog_service.module.book.domain.handler.UpdateBookCoverHandler;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateBookCoverHandler Unit Tests")
class UpdateBookCoverHandlerTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private UpdateBookCoverHandler updateBookCoverHandler;

    @Test
    @DisplayName("Should update cover URL and save book when book exists")
    void shouldUpdateCoverAndSaveBook() {
        UUID bookId = UUID.randomUUID();
        Book book = mock(Book.class);
        String coverUrl = "/files/books/" + bookId + "-abc.png";

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));

        updateBookCoverHandler.execute(bookId, coverUrl);

        then(book).should().updateCoverUrl(coverUrl);
        then(bookRepository).should().save(book);
    }

    @Test
    @DisplayName("Should throw BookNotFoundException when book does not exist")
    void shouldThrowNotFoundWhenBookMissing() {
        UUID bookId = UUID.randomUUID();
        given(bookRepository.findById(bookId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> updateBookCoverHandler.execute(bookId, "/files/books/test.png"))
                .isInstanceOf(BookNotFoundException.class)
                .hasMessageContaining(bookId.toString());
    }
}
