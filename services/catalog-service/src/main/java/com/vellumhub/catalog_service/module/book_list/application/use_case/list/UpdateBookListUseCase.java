package com.vellumhub.catalog_service.module.book_list.application.use_case.list;

import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListResourceNotFoundException;
import com.vellumhub.catalog_service.module.book_list.application.command.list.UpdateBookListCommand;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListPermissionDeniedException;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListDomainException;
import com.vellumhub.catalog_service.module.book_list.domain.model.BookList;
import com.vellumhub.catalog_service.module.book_list.domain.port.BookListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateBookListUseCase {

    private final BookListRepository bookListRepository;

    public UpdateBookListUseCase(BookListRepository bookListRepository) {
        this.bookListRepository = bookListRepository;
    }

    @Transactional
    public BookList execute(UpdateBookListCommand command) {
        var bookList = bookListRepository.findById(command.bookListId())
                .orElseThrow(BookListResourceNotFoundException::new);

        if(!bookList.canUpdate(command.userId())) {
            throw new BookListPermissionDeniedException();
        }

        bookList.update(
                command.name(),
                command.description(),
                command.typeBookList()
        );

        return bookListRepository.save(bookList);
    }

}
