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

import java.util.Optional;
import java.util.Set;
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
        @DisplayName("Should approve book request successfully, create book, delete request and send event")
        void shouldApproveBookRequestSuccessfully() {
            // Given
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

            // Simulating database save behavior by returning a book with an ID
            UUID generatedBookId = UUID.randomUUID();
            doAnswer(invocation -> {
                Book savedBook = invocation.getArgument(0);
                // Creating a mock here just to simulate the returned book with an ID for the event
                Book bookWithId = mock(Book.class);
                given(bookWithId.getId()).willReturn(generatedBookId);
                given(bookWithId.getTitle()).willReturn(savedBook.getTitle());
                given(bookWithId.getDescription()).willReturn(savedBook.getDescription());
                given(bookWithId.getReleaseYear()).willReturn(savedBook.getReleaseYear());
                given(bookWithId.getCoverUrl()).willReturn(savedBook.getCoverUrl());
                given(bookWithId.getAuthor()).willReturn(savedBook.getAuthor());

                return bookWithId; // Em um cenário real de Spring Data, o save() mutaciona a entidade ou devolve uma nova
            }).when(bookRepository).save(any(Book.class));

            // When
            approveBookRequestUseCase.execute(requestId);

            // Then
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
            then(producer).should().send(eq("created-book"), anyString(), eventCaptor.capture());

            CreateBookEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.title()).isEqualTo("The Hobbit");
            assertThat(capturedEvent.author()).isEqualTo("J.R.R. Tolkien");
            assertThat(capturedEvent.genres()).containsExactlyInAnyOrder("Fantasy", "Adventure");
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
            then(bookRequestRepository).should(never()).deleteById(anyLong());
            then(producer).shouldHaveNoInteractions();
        }
    }
}