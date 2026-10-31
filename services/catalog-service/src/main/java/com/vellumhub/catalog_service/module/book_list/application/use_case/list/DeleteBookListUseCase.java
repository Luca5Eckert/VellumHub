package com.vellumhub.catalog_service.module.book_list.application.use_case.list;

import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListResourceNotFoundException;
import com.vellumhub.catalog_service.module.book_list.application.command.list.DeleteBookListCommand;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListPermissionDeniedException;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListDomainException;
import com.vellumhub.catalog_service.module.book_list.domain.model.BookList;
import com.vellumhub.catalog_service.module.book_list.domain.port.BookListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteBookListUseCase {

    private final BookListRepository bookListRepository;

    public DeleteBookListUseCase(BookListRepository bookListRepository) {
        this.bookListRepository = bookListRepository;
    }

    @Transactional
    public void execute(DeleteBookListCommand command) {
        BookList bookList = bookListRepository.findById(command.bookListId())
                .orElseThrow(BookListResourceNotFoundException::new);

        if(!bookList.canDelete(command.userId())) {
            throw new BookListPermissionDeniedException();
        }

        bookListRepository.deleteById(command.bookListId());
    }
}
