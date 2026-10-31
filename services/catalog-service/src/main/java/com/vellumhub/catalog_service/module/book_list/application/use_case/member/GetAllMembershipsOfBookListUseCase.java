package com.vellumhub.catalog_service.module.book_list.application.use_case.member;

import com.vellumhub.catalog_service.module.book_list.application.query.member.GetAllMembershipOfBookListQuery;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListResourceNotFoundException;
import com.vellumhub.catalog_service.module.book_list.domain.model.BookListMembership;
import com.vellumhub.catalog_service.module.book_list.domain.port.BookListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetAllMembershipsOfBookListUseCase {

    private final BookListRepository bookListRepository;

    public GetAllMembershipsOfBookListUseCase(BookListRepository bookListRepository) {
        this.bookListRepository = bookListRepository;
    }

    /**
     * Get all memberships of a book list
     * Check if the user has permission to read the book list
     * @param query the query containing the information needed to get all memberships of a book list
     * @return a list of memberships of the book list
     * @throws BookListDomainException if the book list is not found or if the user doesn't have permission to read the book list
     */
    @Transactional(readOnly = true)
    public List<BookListMembership> execute(GetAllMembershipOfBookListQuery query) {
        var bookList = bookListRepository.findById(query.bookListId())
                .orElseThrow(BookListResourceNotFoundException::new);

        if (query.userAuthenticatedId() == null
                || (!bookList.getUserOwner().equals(query.userAuthenticatedId())
                && !bookList.isMember(query.userAuthenticatedId()))) {
            // A public list does not make its membership directory public.
            throw new BookListResourceNotFoundException();
        }

        return List.copyOf(bookList.getMemberships()); // Load within the read-only transaction.
    }

}
