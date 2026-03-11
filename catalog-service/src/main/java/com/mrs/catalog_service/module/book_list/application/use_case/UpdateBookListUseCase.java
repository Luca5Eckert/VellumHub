package com.mrs.catalog_service.module.book_list.application.use_case;

import com.mrs.catalog_service.module.book_list.application.command.UpdateBookListCommand;
import com.mrs.catalog_service.module.book_list.domain.exception.BookListDomainException;
import com.mrs.catalog_service.module.book_list.domain.model.BookList;
import com.mrs.catalog_service.module.book_list.domain.port.BookListRepository;
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
                .orElseThrow(() -> new RuntimeException("Book list not found"));

        if(!bookList.canUpdate(command.userId())) {
            throw new BookListDomainException("User don't have permission to update this book list");
        }

        bookList.update(
                command.name(),
                command.description(),
                command.typeBookList()
        );

        return bookListRepository.save(bookList);
    }

}
