package com.vellumhub.catalog_service.module.book_list.application.use_case.member;

import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListResourceNotFoundException;
import com.vellumhub.catalog_service.module.book_list.application.command.member.UpdateMemberRoleCommand;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListPermissionDeniedException;
import com.vellumhub.catalog_service.module.book_list.domain.exception.MembershipBookListDomainException;
import com.vellumhub.catalog_service.module.book_list.domain.port.MembershipBookListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateMemberRoleUseCase {

    private final MembershipBookListRepository membershipBookListRepository;

    public UpdateMemberRoleUseCase(MembershipBookListRepository membershipBookListRepository) {
        this.membershipBookListRepository = membershipBookListRepository;
    }

    /**
     * Update the role of a member in a book list.
     * The user must have permission to update the role of a member in the book list.
     * @param command the command containing the information needed to update the role of a member in a book list
     * @throws MembershipBookListDomainException if the membership is not found or if the user doesn't have permission to update the role of a member in the book list
     */
    @Transactional
    public void execute(UpdateMemberRoleCommand command) {
        var membership = membershipBookListRepository.findById(command.membershipId())
                .orElseThrow(BookListResourceNotFoundException::new);
        var bookList = membership.getBookList();

        if(!bookList.canUpdateRole(command.userAuthenticatedId())){
            throw new BookListPermissionDeniedException();
        }

        membership.updateRole(command.newRole());

        membershipBookListRepository.save(membership);
    }

}
