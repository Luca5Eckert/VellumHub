package com.mrs.catalog_service.module.book_request.domain.use_case;

import com.mrs.catalog_service.module.book.domain.event.CreateBookEvent;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.model.Genre;
import com.mrs.catalog_service.module.book.domain.port.BookEventProducer;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import com.mrs.catalog_service.module.book_request.domain.BookRequest;
import com.mrs.catalog_service.module.book_request.domain.exception.BookRequestDomainException;
import com.mrs.catalog_service.module.book_request.domain.port.BookRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApproveBookRequestUseCase Unit Tests")
class ApproveBookRequestUseCaseTest {

    @Mock
    private BookRequestRepository bookRequestRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookEventProducer<String, CreateBookEvent> producer;

    @InjectMocks
    private ApproveBookRequestUseCase approveBookRequestUseCase;

    @Nested
    @DisplayName("Success scenarios")
    class SuccessScenarios {

        @Test
        @DisplayName("Should approve book request successfully and create book")
        void shouldApproveBookRequestSuccessfully() {
            // Given
            long requestId = 1L;
            List<Genre> genres = List.of(Genre.FANTASY, Genre.ROMANCE);
            
            BookRequest bookRequest = BookRequest.builder()
                    .id(requestId)
                    .title("The Hobbit")
                    .author("J.R.R. Tolkien")
                    .isbn("978-0-261-10221-4")
                    .description("A fantasy novel")
                    .genres(genres)
                    .pageCount(310)
                    .publisher("George Allen & Unwin")
                    .build();

            given(bookRequestRepository.findById(requestId)).willReturn(Optional.of(bookRequest));
            
            // Mock save to set an ID on the book (simulating database behavior)
            doAnswer(invocation -> {
                Book book = invocation.getArgument(0);
                // Use reflection to set the ID since it's generated
                java.lang.reflect.Field idField = Book.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(book, UUID.randomUUID());
                return null;
            }).when(bookRepository).save(any(Book.class));

            // When
            approveBookRequestUseCase.execute(requestId);

            // Then
            ArgumentCaptor<Book> bookCaptor = ArgumentCaptor.forClass(Book.class);
            then(bookRepository).should().save(bookCaptor.capture());

            Book savedBook = bookCaptor.getValue();
            assertThat(savedBook.getTitle()).isEqualTo("The Hobbit");
            assertThat(savedBook.getAuthor()).isEqualTo("J.R.R. Tolkien");
            assertThat(savedBook.getIsbn()).isEqualTo("978-0-261-10221-4");
            assertThat(savedBook.getDescription()).isEqualTo("A fantasy novel");
            assertThat(savedBook.getGenres()).containsExactlyInAnyOrderElementsOf(genres);
            assertThat(savedBook.getPageCount()).isEqualTo(310);
            assertThat(savedBook.getPublisher()).isEqualTo("George Allen & Unwin");

            then(bookRequestRepository).should().deleteById(requestId);
        }

        @Test
        @DisplayName("Should produce CreateBookEvent after approving book request")
        void shouldProduceEventAfterApproving() {
            // Given
            long requestId = 1L;
            List<Genre> genres = List.of(Genre.SCI_FI);

            BookRequest bookRequest = BookRequest.builder()
                    .id(requestId)
                    .title("1984")
                    .author("George Orwell")
                    .isbn("978-0-452-28423-4")
                    .description("A dystopian novel")
                    .genres(genres)
                    .pageCount(328)
                    .publisher("Secker & Warburg")
                    .build();

            given(bookRequestRepository.findById(requestId)).willReturn(Optional.of(bookRequest));
            
            // Mock save to set an ID on the book (simulating database behavior)
            doAnswer(invocation -> {
                Book book = invocation.getArgument(0);
                // Use reflection to set the ID since it's generated
                java.lang.reflect.Field idField = Book.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(book, UUID.randomUUID());
                return null;
            }).when(bookRepository).save(any(Book.class));

            // When
            approveBookRequestUseCase.execute(requestId);

            // Then
            ArgumentCaptor<CreateBookEvent> eventCaptor = ArgumentCaptor.forClass(CreateBookEvent.class);
            then(producer).should().send(eq("created-book"), any(String.class), eventCaptor.capture());

            CreateBookEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.genres()).containsExactlyInAnyOrderElementsOf(genres);
        }
    }

    @Nested
    @DisplayName("Failure scenarios")
    class FailureScenarios {

        @Test
        @DisplayName("Should throw exception when book request not found")
        void shouldThrowExceptionWhenRequestNotFound() {
            // Given
            long requestId = 999L;
            given(bookRequestRepository.findById(requestId)).willReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> approveBookRequestUseCase.execute(requestId))
                    .isInstanceOf(BookRequestDomainException.class)
                    .hasMessageContaining("Book request not found");

            then(bookRepository).shouldHaveNoInteractions();
            then(producer).shouldHaveNoInteractions();
        }
    }
}
