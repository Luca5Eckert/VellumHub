package com.vellumhub.catalog_service.module.book_list.application.use_case.member;

import com.vellumhub.catalog_service.module.book_list.application.query.member.GetMembershipQuery;
import com.vellumhub.catalog_service.module.book_list.domain.exception.MembershipBookListDomainException;
import com.vellumhub.catalog_service.module.book_list.domain.model.BookListMembership;
import com.vellumhub.catalog_service.module.book_list.domain.port.MembershipBookListRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetMembershipBookListUseCaseTest {

    @Mock
    private MembershipBookListRepository membershipRepository;

    @InjectMocks
    private GetMembershipBookListUseCase useCase;

    @Test
    @DisplayName("Should return a specific membership by its ID")
    void shouldReturnSingleMembershipById() {
        // Arrange
        var membershipId = UUID.randomUUID();
        var membership = new BookListMembership();
        membership.setId(membershipId);

        var query = new GetMembershipQuery(membershipId);
        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(membership));

        // Act
        var result = useCase.execute(query);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(membershipId);
    }

    @Test
    @DisplayName("Should throw exception when requested membership does not exist")
    void shouldThrowExceptionWhenNotFound() {
        // Arrange
        var query = new GetMembershipQuery(UUID.randomUUID());
        when(membershipRepository.findById(query.membershipId())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(query))
                .isInstanceOf(MembershipBookListDomainException.class)
                .hasMessage("Membership not found");
    }
}