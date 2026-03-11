package com.mrs.catalog_service.module.book_list.application.use_case;

import com.mrs.catalog_service.module.book_list.application.query.GetBookListByIdQuery;
import com.mrs.catalog_service.module.book_list.domain.exception.BookListDomainException;
import com.mrs.catalog_service.module.book_list.domain.model.BookList;
import com.mrs.catalog_service.module.book_list.domain.model.TypeBookList;
import com.mrs.catalog_service.module.book_list.domain.port.BookListRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GetBookListByIdUseCaseTest {

    @Mock
    private BookListRepository bookListRepository;

    @InjectMocks
    private GetBookListByIdUseCase getBookListByIdUseCase;

    @Test
    @DisplayName("Should successfully return the BookList when it exists and user has read permission")
    void shouldReturnBookListWhenExistsAndUserHasPermission() {
        // Arrange
        UUID ownerId = UUID.randomUUID();
        UUID bookListId = UUID.randomUUID();
        GetBookListByIdQuery query = GetBookListByIdQuery.of(ownerId, bookListId);

        BookList mockBookList = BookList.create("My List", "Desc", TypeBookList.PRIVATE, ownerId, new ArrayList<>());

        given(bookListRepository.findByIdFull(bookListId)).willReturn(Optional.of(mockBookList));

        // Act
        BookList result = getBookListByIdUseCase.execute(query);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("My List");
        verify(bookListRepository).findByIdFull(bookListId);
    }

    @Test
    @DisplayName("Should throw BookListDomainException when the BookList is not found in the database")
    void shouldThrowExceptionWhenBookListNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID bookListId = UUID.randomUUID();
        GetBookListByIdQuery query = GetBookListByIdQuery.of(userId, bookListId);

        given(bookListRepository.findByIdFull(bookListId)).willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> getBookListByIdUseCase.execute(query))
                .isInstanceOf(BookListDomainException.class)
                .hasMessage("Book list not found");

        verify(bookListRepository).findByIdFull(bookListId);
    }

    @Test
    @DisplayName("Should throw BookListDomainException when user doesn't have read permission")
    void shouldThrowExceptionWhenUserLacksReadPermission() {
        // Arrange
        UUID ownerId = UUID.randomUUID();
        UUID intruderId = UUID.randomUUID();
        UUID bookListId = UUID.randomUUID();
        GetBookListByIdQuery query = GetBookListByIdQuery.of(intruderId, bookListId);

        BookList mockPrivateList = BookList.create("Secret List", "Desc", TypeBookList.PRIVATE, ownerId, new ArrayList<>());

        given(bookListRepository.findByIdFull(bookListId)).willReturn(Optional.of(mockPrivateList));

        // Act & Assert
        assertThatThrownBy(() -> getBookListByIdUseCase.execute(query))
                .isInstanceOf(BookListDomainException.class)
                .hasMessage("User don't have permission to read this book list");

        verify(bookListRepository).findByIdFull(bookListId);
    }
}