package com.mrs.catalog_service.module.book_list.application.use_case.member;

import com.mrs.catalog_service.module.book_list.application.command.member.UpdateRoleOfMembershipCommand;
import com.mrs.catalog_service.module.book_list.domain.exception.MembershipBookListDomainException;
import com.mrs.catalog_service.module.book_list.domain.port.MembershipBookListRepository;
import org.springframework.stereotype.Service;

@Service
public class UpdateRoleOfMembershipUseCase {

    private final MembershipBookListRepository membershipBookListRepository;

    public UpdateRoleOfMembershipUseCase(MembershipBookListRepository membershipBookListRepository) {
        this.membershipBookListRepository = membershipBookListRepository;
    }

    public void execute(UpdateRoleOfMembershipCommand command) {
        var membership = membershipBookListRepository.findById(command.membershipId())
                .orElseThrow(() -> new MembershipBookListDomainException("Membership not found"));
        var bookList = membership.getBookList();

        if(bookList.canUpdateRole(command.userAuthenticatedId())){
            throw new MembershipBookListDomainException("User don't have permission to update role of membership in this book list");
        }

        membership.updateRole(command.newRole());

        membershipBookListRepository.save(membership);
    }

}
