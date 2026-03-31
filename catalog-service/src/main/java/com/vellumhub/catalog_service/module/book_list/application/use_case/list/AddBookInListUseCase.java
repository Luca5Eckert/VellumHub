package com.vellumhub.catalog_service.module.book_list.application.use_case.list;

import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import com.vellumhub.catalog_service.module.book_list.application.command.list.AddBookInListCommand;
import com.vellumhub.catalog_service.module.book_list.domain.port.BookListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddBookInListUseCase {

    private final BookListRepository bookListRepository;
    private final BookRepository bookRepository;

    public AddBookInListUseCase(BookListRepository bookListRepository, BookRepository bookRepository) {
        this.bookListRepository = bookListRepository;
        this.bookRepository = bookRepository;
    }

    @Transactional
    public void execute(AddBookInListCommand command){
        var bookList = bookListRepository.findById(command.listId())
                .orElseThrow(() -> new RuntimeException("Book list not found"));

        var book = bookRepository.findById(command.bookId())
                .orElseThrow(() -> new RuntimeException("Book not found"));

        bookList.addBook(book, command.userId());

        bookListRepository.save(bookList);
    }
}
