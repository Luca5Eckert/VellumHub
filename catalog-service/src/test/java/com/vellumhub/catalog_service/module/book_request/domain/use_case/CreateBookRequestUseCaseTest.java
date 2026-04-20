package com.vellumhub.catalog_service.module.book_request.domain.use_case;

import com.vellumhub.catalog_service.module.book.domain.model.Genre;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import com.vellumhub.catalog_service.module.book.domain.port.GenreRepository;
import com.vellumhub.catalog_service.module.book_request.domain.BookRequest;
import com.vellumhub.catalog_service.module.book_request.domain.command.CreateBookRequestCommand;
import com.vellumhub.catalog_service.module.book_request.domain.exception.BookAlreadyExistInCatalogException;
import com.vellumhub.catalog_service.module.book_request.domain.exception.BookRequestAlreadyExistException;
import com.vellumhub.catalog_service.module.book_request.domain.port.BookRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateBookRequestUseCaseTest {

    @Mock
    private BookRequestRepository bookRequestRepository;

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private CreateBookRequestUseCase createBookRequestUseCase;

    @Test
    @DisplayName("Should create a book request when there are no duplicates")
    void shouldCreateBookRequestSuccessfully() {
        CreateBookRequestCommand command = createCommand();
        when(bookRepository.existByTitleAndAuthor(command.title(), command.author())).thenReturn(false);
        when(bookRequestRepository.existByTitleAndAuthor(command.title(), command.author())).thenReturn(false);
        when(genreRepository.findByName("FANTASY")).thenReturn(Optional.of(new Genre("FANTASY")));
        when(genreRepository.findByName("HORROR")).thenReturn(Optional.of(new Genre("HORROR")));

        BookRequest result = createBookRequestUseCase.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo(command.title());
        assertThat(result.getGenres())
                .extracting(Genre::getName)
                .containsExactlyInAnyOrder("FANTASY", "HORROR");

        ArgumentCaptor<BookRequest> captor = ArgumentCaptor.forClass(BookRequest.class);
        verify(bookRequestRepository, times(1)).save(captor.capture());

        BookRequest savedRequest = captor.getValue();
        assertThat(savedRequest.getIsbn()).isEqualTo(command.isbn());
        assertThat(savedRequest.getAuthor()).isEqualTo(command.author());
    }

    @Test
    @DisplayName("Should throw an exception when the book already exists in the catalog")
    void shouldThrowExceptionWhenBookAlreadyExistsInCatalog() {
        CreateBookRequestCommand command = createCommand();
        when(bookRepository.existByTitleAndAuthor(command.title(), command.author())).thenReturn(true);

        assertThatThrownBy(() -> createBookRequestUseCase.execute(command))
                .isInstanceOf(BookAlreadyExistInCatalogException.class);

        verify(bookRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw an exception when a matching request already exists")
    void shouldThrowExceptionWhenBookRequestAlreadyExists() {
        CreateBookRequestCommand command = createCommand();
        when(bookRepository.existByTitleAndAuthor(command.title(), command.author())).thenReturn(false);
        when(bookRequestRepository.existByTitleAndAuthor(command.title(), command.author())).thenReturn(true);

        assertThatThrownBy(() -> createBookRequestUseCase.execute(command))
                .isInstanceOf(BookRequestAlreadyExistException.class);

        verify(bookRequestRepository, never()).save(any());
    }

    private CreateBookRequestCommand createCommand() {
        return new CreateBookRequestCommand(
                "The Pragmatic Programmer",
                "Practical software craftsmanship",
                1999,
                "http://image.com/cover.jpg",
                "Andy Hunt",
                "9780201616224",
                352,
                "Addison-Wesley",
                List.of("FANTASY", "HORROR")
        );
    }
}
