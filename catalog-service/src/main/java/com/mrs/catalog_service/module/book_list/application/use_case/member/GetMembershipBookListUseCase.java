package com.mrs.catalog_service.module.book_list.application.use_case.member;

import com.mrs.catalog_service.module.book_list.application.query.member.GetMembershipQuery;
import com.mrs.catalog_service.module.book_list.domain.exception.MembershipBookListDomainException;
import com.mrs.catalog_service.module.book_list.domain.model.BookListMembership;
import com.mrs.catalog_service.module.book_list.domain.port.MembershipBookListRepository;
import org.springframework.stereotype.Service;

@Service
public class GetMembershipBookListUseCase {

    private final MembershipBookListRepository membershipBookListRepository;

    public GetMembershipBookListUseCase(MembershipBookListRepository membershipBookListRepository) {
        this.membershipBookListRepository = membershipBookListRepository;
    }

    public BookListMembership execute(GetMembershipQuery query){
        return membershipBookListRepository.findById(query.membershipId())
                .orElseThrow(() -> new MembershipBookListDomainException("Membership not found"));
    }

}
