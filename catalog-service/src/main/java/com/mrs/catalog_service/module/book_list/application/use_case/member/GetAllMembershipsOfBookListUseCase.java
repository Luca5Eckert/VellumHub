package com.mrs.catalog_service.module.book_list.application.use_case.member;

import com.mrs.catalog_service.module.book_list.application.query.member.GetAllMembershipOfBookListQuery;
import com.mrs.catalog_service.module.book_list.domain.exception.BookListDomainException;
import com.mrs.catalog_service.module.book_list.domain.model.BookListMembership;
import com.mrs.catalog_service.module.book_list.domain.port.BookListRepository;
import org.springframework.stereotype.Service;

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
    public List<BookListMembership> execute(GetAllMembershipOfBookListQuery query) {
        var bookList = bookListRepository.findById(query.bookListId())
                .orElseThrow(() -> new BookListDomainException("Book list not found"));

        if(!bookList.canRead(query.userAuthenticatedId())) {
            throw new BookListDomainException("User doesn't have permission to read this book list");
        }

        return bookList.getMemberships();
    }

}
