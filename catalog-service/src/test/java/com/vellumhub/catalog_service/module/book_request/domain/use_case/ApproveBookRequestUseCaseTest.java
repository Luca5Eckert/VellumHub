package com.vellumhub.catalog_service.module.book_request.domain.use_case;

import com.vellumhub.catalog_service.module.book.domain.event.CreateBookEvent;
import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.model.Genre;
import com.vellumhub.catalog_service.module.book.domain.port.BookEventProducer;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import com.vellumhub.catalog_service.module.book_request.domain.BookRequest;
import com.vellumhub.catalog_service.module.book_request.domain.exception.BookRequestDomainException;
import com.vellumhub.catalog_service.module.book_request.domain.port.BookRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
        @DisplayName("Should approve the request, persist the book, delete the request, and publish the event")
        void shouldApproveBookRequestSuccessfully() {
            long requestId = 1L;
            Genre fantasyGenre = mock(Genre.class);
            given(fantasyGenre.getName()).willReturn("Fantasy");

            Genre adventureGenre = mock(Genre.class);
            given(adventureGenre.getName()).willReturn("Adventure");

            Set<Genre> genres = Set.of(fantasyGenre, adventureGenre);

            BookRequest bookRequest = BookRequest.builder()
                    .id(requestId)
                    .title("The Hobbit")
                    .author("J.R.R. Tolkien")
                    .isbn("978-0-261-10221-4")
                    .description("A fantasy novel")
                    .releaseYear(1937)
                    .coverUrl("http://cover.com/hobbit.jpg")
                    .genres(genres)
                    .pageCount(310)
                    .publisher("George Allen & Unwin")
                    .build();

            given(bookRequestRepository.findById(requestId)).willReturn(Optional.of(bookRequest));

            UUID generatedBookId = UUID.randomUUID();
            doAnswer(invocation -> {
                Book savedBook = invocation.getArgument(0);
                ReflectionTestUtils.setField(savedBook, "id", generatedBookId);
                return null;
            }).when(bookRepository).save(any(Book.class));

            approveBookRequestUseCase.execute(requestId);

            ArgumentCaptor<Book> bookCaptor = ArgumentCaptor.forClass(Book.class);
            then(bookRepository).should().save(bookCaptor.capture());

            Book bookToSave = bookCaptor.getValue();
            assertThat(bookToSave.getTitle()).isEqualTo("The Hobbit");
            assertThat(bookToSave.getAuthor()).isEqualTo("J.R.R. Tolkien");
            assertThat(bookToSave.getIsbn()).isEqualTo("978-0-261-10221-4");
            assertThat(bookToSave.getDescription()).isEqualTo("A fantasy novel");
            assertThat(bookToSave.getReleaseYear()).isEqualTo(1937);
            assertThat(bookToSave.getCoverUrl()).isEqualTo("http://cover.com/hobbit.jpg");
            assertThat(bookToSave.getGenres()).containsExactlyInAnyOrderElementsOf(genres);
            assertThat(bookToSave.getPageCount()).isEqualTo(310);
            assertThat(bookToSave.getPublisher()).isEqualTo("George Allen & Unwin");

            then(bookRequestRepository).should().deleteById(requestId);

            ArgumentCaptor<CreateBookEvent> eventCaptor = ArgumentCaptor.forClass(CreateBookEvent.class);
            then(producer).should().send(eq("created-book"), eq(generatedBookId.toString()), eventCaptor.capture());

            CreateBookEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.bookId()).isEqualTo(generatedBookId);
            assertThat(capturedEvent.title()).isEqualTo("The Hobbit");
            assertThat(capturedEvent.releaseYear()).isEqualTo(1937);
            assertThat(capturedEvent.coverUrl()).isEqualTo("http://cover.com/hobbit.jpg");
            assertThat(capturedEvent.author()).isEqualTo("J.R.R. Tolkien");
            assertThat(capturedEvent.genres()).containsExactlyInAnyOrder("Fantasy", "Adventure");
        }
    }

    @Nested
    @DisplayName("Failure scenarios")
    class FailureScenarios {

        @Test
        @DisplayName("Should throw an exception when the request does not exist")
        void shouldThrowExceptionWhenRequestNotFound() {
            long requestId = 999L;
            given(bookRequestRepository.findById(requestId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> approveBookRequestUseCase.execute(requestId))
                    .isInstanceOf(BookRequestDomainException.class)
                    .hasMessageContaining("Book request not found");

            then(bookRepository).shouldHaveNoInteractions();
            then(bookRequestRepository).should(never()).deleteById(anyLong());
            then(producer).shouldHaveNoInteractions();
        }
    }
}
