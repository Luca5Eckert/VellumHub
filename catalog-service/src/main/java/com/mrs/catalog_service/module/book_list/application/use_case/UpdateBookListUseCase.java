package com.mrs.catalog_service.module.book_list.application.use_case;

import com.mrs.catalog_service.module.book_list.application.command.UpdateBookListCommand;
import com.mrs.catalog_service.module.book_list.domain.model.BookList;
import com.mrs.catalog_service.module.book_list.domain.port.BookListRepository;
import org.springframework.stereotype.Component;

@Component
public class UpdateBookListUseCase {

    private final BookListRepository bookListRepository;

    public UpdateBookListUseCase(BookListRepository bookListRepository) {
        this.bookListRepository = bookListRepository;
    }

    public BookList execute(UpdateBookListCommand command) {
        var bookList = bookListRepository.findById(command.bookListId())
                .orElseThrow(() -> new RuntimeException("Book list not found"));

        bookList.update(
                command.name(),
                command.description(),
                command.typeBookList()
        );

        return bookListRepository.save(bookList);
    }

}
