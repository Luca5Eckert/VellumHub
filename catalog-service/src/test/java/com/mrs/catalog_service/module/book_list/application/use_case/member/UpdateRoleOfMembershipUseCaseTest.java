package com.mrs.catalog_service.module.book_list.application.use_case.member;

import com.mrs.catalog_service.module.book_list.application.command.member.UpdateRoleOfMembershipCommand;
import com.mrs.catalog_service.module.book_list.domain.exception.MembershipBookListDomainException;
import com.mrs.catalog_service.module.book_list.domain.model.BookList;
import com.mrs.catalog_service.module.book_list.domain.model.BookListMembership;
import com.mrs.catalog_service.module.book_list.domain.model.MembershipRole;
import com.mrs.catalog_service.module.book_list.domain.model.TypeBookList;
import com.mrs.catalog_service.module.book_list.domain.port.MembershipBookListRepository;
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
class UpdateRoleOfMembershipUseCaseTest {

    @Mock
    private MembershipBookListRepository membershipRepository;

    @InjectMocks
    private UpdateRoleOfMembershipUseCase useCase;

    @Test
    @DisplayName("Should successfully update role when user is owner/admin")
    void shouldUpdateRoleSuccessfully() {
        // Arrange
        var ownerId = UUID.randomUUID();
        var bookList = BookList.create("Dev Books", "Tech", TypeBookList.PUBLIC, ownerId, List.of());
        var memberId = UUID.randomUUID();
        var membership = BookListMembership.create(bookList, memberId, MembershipRole.VIEWER);

        var command = new UpdateRoleOfMembershipCommand(MembershipRole.ADMIN, ownerId, UUID.randomUUID());
        when(membershipRepository.findById(any())).thenReturn(Optional.of(membership));

        // Act
        useCase.execute(command);

        // Assert
        verify(membershipRepository).save(membership);
        assertThat(membership.getRole()).isEqualTo(MembershipRole.ADMIN);
    }

    @Test
    @DisplayName("Should throw exception when membership is not found")
    void shouldThrowExceptionWhenMembershipNotFound() {
        // Arrange
        var command = new UpdateRoleOfMembershipCommand(MembershipRole.ADMIN, UUID.randomUUID(), UUID.randomUUID());
        when(membershipRepository.findById(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(MembershipBookListDomainException.class)
                .hasMessage("Membership not found");
    }

    @Test
    @DisplayName("Should throw exception when trying to change the OWNER's role")
    void shouldThrowExceptionWhenChangingOwnerRole() {
        // Arrange
        var ownerId = UUID.randomUUID();
        var bookList = BookList.create("Dev Books", "Tech", TypeBookList.PUBLIC, ownerId, List.of());
        var ownerMembership = bookList.getMemberships().getFirst();

        var command = new UpdateRoleOfMembershipCommand(MembershipRole.ADMIN, ownerId, UUID.randomUUID());
        when(membershipRepository.findById(any())).thenReturn(Optional.of(ownerMembership));

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(MembershipBookListDomainException.class)
                .hasMessage("Cannot change the role of the owner");
    }
}