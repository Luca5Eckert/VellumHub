package com.vellumhub.catalog_service.module.book_list.application.use_case.list;

import com.vellumhub.catalog_service.module.book_list.application.query.list.GetBookListByIdQuery;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListResourceNotFoundException;
import com.vellumhub.catalog_service.module.book_list.domain.model.BookList;
import com.vellumhub.catalog_service.module.book_list.domain.port.BookListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetBookListByIdUseCase {

    private BookListRepository bookListRepository;

    public GetBookListByIdUseCase(BookListRepository bookListRepository) {
        this.bookListRepository = bookListRepository;
    }

    @Transactional(readOnly = true)
    public BookList execute(GetBookListByIdQuery query){
        BookList bookList = bookListRepository.findByIdFull(query.bookListId())
                .orElseThrow(BookListResourceNotFoundException::new);

        if(!bookList.canRead(query.userId())) {
            throw new BookListResourceNotFoundException();
        }

        // Materialize books while the transaction is open; fetching two List bags
        // in one Hibernate query is not supported, and the controller maps later.
        bookList.getBooks().size();
        return bookList;
    }

}
