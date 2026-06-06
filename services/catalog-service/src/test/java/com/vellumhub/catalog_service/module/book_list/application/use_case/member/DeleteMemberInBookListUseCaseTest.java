package com.vellumhub.catalog_service.module.book_list.application.use_case.member;

import com.vellumhub.catalog_service.module.book_list.application.command.member.DeleteMemberInBookListCommand;
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
class DeleteMemberInBookListUseCaseTest {

    @Mock
    private BookListRepository bookListRepository;

    @InjectMocks
    private DeleteMemberInBookListUseCase useCase;

    @Test
    @DisplayName("Should successfully delete a member when user has permission")
    void shouldDeleteMemberSuccessfully() {
        var ownerId = UUID.randomUUID();
        var memberId = UUID.randomUUID();
        var bookList = BookList.create("Fantasy List", "Desc", TypeBookList.PUBLIC, ownerId, List.of());
        bookList.addMember(memberId, MembershipRole.VIEWER);

        var command = new DeleteMemberInBookListCommand(ownerId, memberId, bookList.getId());
        when(bookListRepository.findById(any())).thenReturn(Optional.of(bookList));

        useCase.execute(command);

        verify(bookListRepository).save(bookList);
        assertThat(bookList.isMember(memberId)).isFalse();
    }

    @Test
    @DisplayName("Should throw exception when attempting to delete the owner")
    void shouldThrowExceptionWhenTryingToRemoveOwner() {
        var ownerId = UUID.randomUUID();
        var bookList = BookList.create("Fantasy List", "Desc", TypeBookList.PUBLIC, ownerId, List.of());
        var command = new DeleteMemberInBookListCommand(ownerId, ownerId, bookList.getId());

        when(bookListRepository.findById(any())).thenReturn(Optional.of(bookList));

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(BookListDomainException.class)
                .hasMessage("Owner can't be removed from the book list");
    }

    @Test
    @DisplayName("Should throw exception when user lacks permission to delete members")
    void shouldThrowExceptionWhenUserLacksPermission() {
        var ownerId = UUID.randomUUID();
        var viewerId = UUID.randomUUID();
        var targetMemberId = UUID.randomUUID();
        var bookList = BookList.create("Fantasy List", "Desc", TypeBookList.PRIVATE, ownerId, List.of());
        bookList.addMember(viewerId, MembershipRole.VIEWER);
        bookList.addMember(targetMemberId, MembershipRole.VIEWER);

        var command = new DeleteMemberInBookListCommand(viewerId, targetMemberId, bookList.getId());
        when(bookListRepository.findById(any())).thenReturn(Optional.of(bookList));

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(BookListDomainException.class)
                .hasMessage("User doesn't have permission to delete a member from this book list");
    }
}
