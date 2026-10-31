package com.vellumhub.catalog_service.module.book_list.application.use_case.member;

import com.vellumhub.catalog_service.module.book_list.application.query.member.GetMembershipQuery;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListResourceNotFoundException;
import com.vellumhub.catalog_service.module.book_list.domain.model.BookListMembership;
import com.vellumhub.catalog_service.module.book_list.domain.port.MembershipBookListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetMembershipBookListUseCase {

    private final MembershipBookListRepository membershipBookListRepository;

    public GetMembershipBookListUseCase(MembershipBookListRepository membershipBookListRepository) {
        this.membershipBookListRepository = membershipBookListRepository;
    }

    /**
     * Get a membership by id
     * Check if the membership exists, if not throw an exception
     * @param query the query with the membership id
     * @return the membership
     */
    @Transactional(readOnly = true)
    public BookListMembership execute(GetMembershipQuery query){
        var membership = membershipBookListRepository.findById(query.membershipId())
                .orElseThrow(BookListResourceNotFoundException::new);
        var bookList = membership.getBookList();
        if (query.userAuthenticatedId() == null || bookList == null) {
            throw new BookListResourceNotFoundException();
        }
        if (!query.userAuthenticatedId().equals(membership.getUserId())
                && !query.userAuthenticatedId().equals(bookList.getUserOwner())
                && !bookList.isAdmin(query.userAuthenticatedId())) {
            throw new BookListResourceNotFoundException();
        }
        return membership;
    }

}
