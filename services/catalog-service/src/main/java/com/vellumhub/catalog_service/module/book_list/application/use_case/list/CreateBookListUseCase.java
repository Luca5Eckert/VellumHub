package com.vellumhub.catalog_service.module.book_list.application.use_case.list;

import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import com.vellumhub.catalog_service.module.book_list.application.command.list.CreateBookListCommand;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListDomainException;
import com.vellumhub.catalog_service.module.book_list.domain.model.BookList;
import com.vellumhub.catalog_service.module.book_list.domain.port.BookListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CreateBookListUseCase {

    private final BookListRepository bookListRepository;
    private final BookRepository bookRepository;

    public CreateBookListUseCase(BookListRepository bookListRepository, BookRepository bookRepository) {
        this.bookListRepository = bookListRepository;
        this.bookRepository = bookRepository;
    }

    @Transactional
    public BookList execute(CreateBookListCommand command) {
        var books = getBooks(command.booksId());

        var bookList = BookList.create(
                command.title(),
                command.description(),
                command.type(),
                command.userId(),
                books
        );

        return bookListRepository.save(bookList);
    }

    private List<Book> getBooks(List<UUID> bookIds) {
        if(bookIds == null || bookIds.isEmpty()) return List.of();

        var uniqueIds = Set.copyOf(bookIds);
        var books = bookRepository.findAllById(uniqueIds);

        if (books.size() != uniqueIds.size()) {
            throw new BookListDomainException("One or more books don't exists");
        }

        return books;
    }


}
