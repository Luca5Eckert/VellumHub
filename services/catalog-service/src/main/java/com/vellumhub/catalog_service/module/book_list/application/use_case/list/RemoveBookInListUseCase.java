package com.vellumhub.catalog_service.module.book_list.application.use_case.list;

import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import com.vellumhub.catalog_service.module.book_list.application.command.member.RemoveBookInListCommand;
import com.vellumhub.catalog_service.module.book_list.domain.port.BookListRepository;
import org.springframework.stereotype.Service;

@Service
public class RemoveBookInListUseCase {

    private final BookListRepository bookListRepository;
    private final BookRepository bookRepository;

    public RemoveBookInListUseCase(BookListRepository bookListRepository, BookRepository bookRepository) {
        this.bookListRepository = bookListRepository;
        this.bookRepository = bookRepository;
    }

    public void execute(RemoveBookInListCommand command){
        var bookList = bookListRepository.findById(command.listId())
                .orElseThrow(() -> new RuntimeException("Book list not found"));

        var book = bookRepository.findById(command.bookId())
                .orElseThrow(() -> new RuntimeException("Book not found"));

        bookList.removeBook(book, command.userId());

        bookListRepository.save(bookList);
    }
}
