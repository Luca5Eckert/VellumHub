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

    public List<BookListMembership> execute(GetAllMembershipOfBookListQuery query) {
        var bookList = bookListRepository.findById(query.bookListId())
                .orElseThrow(() -> new BookListDomainException("Book list not found"));

        if(!bookList.canRead(query.userAuthenticatedId())) {
            throw new BookListDomainException("User don't have permission to read this book list");
        }

        return bookList.getMemberships();
    }

}
