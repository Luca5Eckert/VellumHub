package com.mrs.catalog_service.module.book_request.domain.use_case;

import com.mrs.catalog_service.module.book.domain.model.Genre;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import com.mrs.catalog_service.module.book_request.domain.BookRequest;
import com.mrs.catalog_service.module.book_request.domain.command.CreateBookRequestCommand;
import com.mrs.catalog_service.module.book_request.domain.exception.BookAlreadyExistInCatalogException;
import com.mrs.catalog_service.module.book_request.domain.exception.BookRequestAlreadyExistException;
import com.mrs.catalog_service.module.book_request.domain.port.BookRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateBookRequestUseCaseTest {

    @Mock
    private BookRequestRepository bookRequestRepository;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private CreateBookRequestUseCase createBookRequestUseCase;

    @Test
    @DisplayName("Deve criar uma solicitação de livro com sucesso quando não houver duplicatas")
    void shouldCreateBookRequestSuccessfully() {
        // Arrange
        var command = createCommand();
        when(bookRepository.existByTitleAndAuthor(command.title(), command.author())).thenReturn(false);
        when(bookRequestRepository.existByTitleAndAuthor(command.title(), command.author())).thenReturn(false);

        // Act
        BookRequest result = createBookRequestUseCase.execute(command);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo(command.title());

        // Verificamos se o repositório realmente recebeu o objeto esperado
        ArgumentCaptor<BookRequest> captor = ArgumentCaptor.forClass(BookRequest.class);
        verify(bookRequestRepository, times(1)).save(captor.capture());

        BookRequest savedRequest = captor.getValue();
        assertThat(savedRequest.getIsbn()).isEqualTo(command.isbn());
        assertThat(savedRequest.getAuthor()).isEqualTo(command.author());
    }

    @Test
    @DisplayName("Deve lançar exceção quando o livro já existir no catálogo principal")
    void shouldThrowExceptionWhenBookAlreadyExistsInCatalog() {
        // Arrange
        var command = createCommand();
        when(bookRepository.existByTitleAndAuthor(command.title(), command.author())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> createBookRequestUseCase.execute(command))
                .isInstanceOf(BookAlreadyExistInCatalogException.class);

        verify(bookRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar exceção quando já existir uma solicitação pendente para o mesmo livro")
    void shouldThrowExceptionWhenBookRequestAlreadyExists() {
        // Arrange
        var command = createCommand();
        when(bookRepository.existByTitleAndAuthor(command.title(), command.author())).thenReturn(false);
        when(bookRequestRepository.existByTitleAndAuthor(command.title(), command.author())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> createBookRequestUseCase.execute(command))
                .isInstanceOf(BookRequestAlreadyExistException.class);

        verify(bookRequestRepository, never()).save(any());
    }

    private CreateBookRequestCommand createCommand() {
        return new CreateBookRequestCommand(
                "O Programador Pragmático",
                "Dicas de desenvolvimento",
                1999,
                "http://image.com/cover.jpg",
                "Addison-Wesley",
                "978-0201616224",
                352,
                "Trama",
                List.of(
                        Genre.ROMANCE,
                        Genre.ACTION
                )
        );
    }
}