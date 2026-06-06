package com.vellumhub.catalog_service.module.book_list.application.use_case.member;

import com.vellumhub.catalog_service.module.book_list.application.query.member.GetAllMembershipOfBookListQuery;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListDomainException;
import com.vellumhub.catalog_service.module.book_list.domain.model.BookList;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAllMembershipsOfBookListUseCaseTest {

    @Mock
    private BookListRepository bookListRepository;

    @InjectMocks
    private GetAllMembershipsOfBookListUseCase useCase;

    @Test
    @DisplayName("Should return all memberships when user has read permission")
    void shouldReturnAllMembershipsSuccessfully() {
        var ownerId = UUID.randomUUID();
        var bookList = BookList.create("Cookbooks", "Food", TypeBookList.PUBLIC, ownerId, List.of());
        var query = new GetAllMembershipOfBookListQuery(bookList.getId(), ownerId);

        when(bookListRepository.findById(any())).thenReturn(Optional.of(bookList));

        var result = useCase.execute(query);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(ownerId);
    }

    @Test
    @DisplayName("Should throw exception when attempting to read private list without permission")
    void shouldThrowExceptionWhenNoReadPermission() {
        var ownerId = UUID.randomUUID();
        var strangerId = UUID.randomUUID();
        var bookList = BookList.create("Diary", "Personal", TypeBookList.PRIVATE, ownerId, List.of());
        var query = new GetAllMembershipOfBookListQuery(bookList.getId(), strangerId);

        when(bookListRepository.findById(any())).thenReturn(Optional.of(bookList));

        assertThatThrownBy(() -> useCase.execute(query))
                .isInstanceOf(BookListDomainException.class)
                .hasMessage("User doesn't have permission to read this book list");
    }
}
