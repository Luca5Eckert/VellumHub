package com.vellumhub.catalog_service.module.book_list.application.use_case.member;

import com.vellumhub.catalog_service.module.book_list.application.command.member.AddMemberInBookListCommand;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListDomainException;
import com.vellumhub.catalog_service.module.book_list.domain.model.BookList;
import com.vellumhub.catalog_service.module.book_list.domain.model.MembershipRole;
import com.vellumhub.catalog_service.module.book_list.domain.model.TypeBookList;
import com.vellumhub.catalog_service.module.book_list.domain.port.BookListRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddMemberInBookListUseCaseTest {

    @Mock
    private BookListRepository bookListRepository;

    @InjectMocks
    private AddMemberInBookListUseCase useCase;

    @Test
    @DisplayName("Should successfully add a new member when user has permission")
    void shouldAddMemberSuccessfully() {
        // Arrange
        var ownerId = UUID.randomUUID();
        var bookListId = UUID.randomUUID();
        var newUser = UUID.randomUUID();
        var bookList = BookList.create("Sci-Fi Favorites", "Best sci-fi books", TypeBookList.PUBLIC, ownerId, List.of());
        var command = new AddMemberInBookListCommand(bookListId, ownerId, newUser, MembershipRole.VIEWER);

        when(bookListRepository.findById(bookListId)).thenReturn(Optional.of(bookList));

        // Act
        useCase.execute(command);

        // Assert
        verify(bookListRepository).save(bookList);
        assertThat(bookList.getMemberships()).hasSize(2); // Owner + New Member
        assertThat(bookList.isMember(newUser)).isTrue();
    }

    @Test
    @DisplayName("Should throw exception when book list does not exist")
    void shouldThrowExceptionWhenBookListNotFound() {
        // Arrange
        var command = new AddMemberInBookListCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), MembershipRole.VIEWER);
        when(bookListRepository.findById(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(BookListDomainException.class)
                .hasMessage("Book list not found");
    }

    @Test
    @DisplayName("Should throw exception when authenticated user lacks permission")
    void shouldThrowExceptionWhenUserHasNoPermission() {
        // Arrange
        var ownerId = UUID.randomUUID();
        var strangerId = UUID.randomUUID();
        var bookList = BookList.create("Private List", "Secret books", TypeBookList.PRIVATE, ownerId, List.of());
        var command = new AddMemberInBookListCommand(bookList.getId(), strangerId, UUID.randomUUID(), MembershipRole.VIEWER);

        when(bookListRepository.findById(any())).thenReturn(Optional.of(bookList));

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(BookListDomainException.class)
                .hasMessage("User don't have permission to add member to this book list");
    }
}