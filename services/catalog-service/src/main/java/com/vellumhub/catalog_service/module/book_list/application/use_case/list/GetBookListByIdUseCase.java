package com.vellumhub.catalog_service.module.book_list.application.use_case.list;

import com.vellumhub.catalog_service.module.book_list.application.query.list.GetBookListByIdQuery;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListDomainException;
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
                .orElseThrow(() -> new BookListDomainException("Book list not found"));

        if(!bookList.canRead(query.userId())) {
            throw new BookListDomainException("User don't have permission to read this book list");
        }

        return bookList;
    }

}
