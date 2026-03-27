package com.vellumhub.catalog_service.module.book_request.domain.use_case;

import com.vellumhub.catalog_service.module.book.domain.model.Genre;
import com.vellumhub.catalog_service.module.book_request.domain.BookRequest;
import com.vellumhub.catalog_service.module.book_request.domain.port.BookRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetAllBookRequestUseCase Unit Tests")
class GetAllBookRequestUseCaseTest {

    @Mock
    private BookRequestRepository bookRequestRepository;

    @InjectMocks
    private GetAllBookRequestUseCase getAllBookRequestUseCase;

    @Nested
    @DisplayName("Success scenarios")
    class SuccessScenarios {

        @Test
        @DisplayName("Should return paginated book requests")
        void shouldReturnPaginatedBookRequests() {
            // Given
            int page = 0;
            int size = 10;

            BookRequest request1 = BookRequest.builder()
                    .id(1L)
                    .title("The Hobbit")
                    .author("J.R.R. Tolkien")
                    .isbn("978-0-261-10221-4")
                    .description("A fantasy novel")
                    .genres(Set.of(new Genre("FANTASY")))
                    .pageCount(310)
                    .publisher("George Allen & Unwin")
                    .build();

            BookRequest request2 = BookRequest.builder()
                    .id(2L)
                    .title("1984")
                    .author("George Orwell")
                    .isbn("978-0-452-28423-4")
                    .description("A dystopian novel")
                    .genres(Set.of(new Genre("SCI-FI")))
                    .pageCount(328)
                    .publisher("Secker & Warburg")
                    .build();

            Page<BookRequest> expectedPage = new PageImpl<>(List.of(request1, request2));
            given(bookRequestRepository.findAll(page, size)).willReturn(expectedPage);

            // When
            Page<BookRequest> result = getAllBookRequestUseCase.execute(page, size);

            // Then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("The Hobbit");
            assertThat(result.getContent().get(1).getTitle()).isEqualTo("1984");
            then(bookRequestRepository).should().findAll(page, size);
        }

        @Test
        @DisplayName("Should return empty page when no book requests exist")
        void shouldReturnEmptyPageWhenNoRequestsExist() {
            // Given
            int page = 0;
            int size = 10;

            Page<BookRequest> emptyPage = new PageImpl<>(Collections.emptyList());
            given(bookRequestRepository.findAll(page, size)).willReturn(emptyPage);

            // When
            Page<BookRequest> result = getAllBookRequestUseCase.execute(page, size);

            // Then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
            then(bookRequestRepository).should().findAll(page, size);
        }

        @Test
        @DisplayName("Should return correct page for pagination")
        void shouldReturnCorrectPageForPagination() {
            // Given
            int page = 2;
            int size = 5;

            BookRequest request = BookRequest.builder()
                    .id(11L)
                    .title("Dune")
                    .author("Frank Herbert")
                    .isbn("978-0-441-17271-9")
                    .description("A science fiction novel")
                    .genres(Set.of(new Genre("SCI-FI")))
                    .pageCount(688)
                    .publisher("Chilton Books")
                    .build();

            Page<BookRequest> expectedPage = new PageImpl<>(List.of(request));
            given(bookRequestRepository.findAll(page, size)).willReturn(expectedPage);

            // When
            Page<BookRequest> result = getAllBookRequestUseCase.execute(page, size);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst().getTitle()).isEqualTo("Dune");
            then(bookRequestRepository).should().findAll(page, size);
        }
    }
}
