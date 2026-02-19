package com.mrs.catalog_service.module.book.domain.service;

import com.mrs.catalog_service.module.book.application.dto.*;
import com.mrs.catalog_service.module.book.application.mapper.BookMapper;
import com.mrs.catalog_service.module.book.domain.handler.*;
import com.mrs.catalog_service.module.book.domain.model.Book;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class BookService {

    private final CreateBookHandler createBookHandler;
    private final DeleteBookHandler deleteBookHandler;
    private final GetBookHandler getBookHandler;
    private final GetAllBooksHandler getAllBooksHandler;
    private final UpdateBookHandler updateBookHandler;
    private final GetBooksByIdsHandler getBooksByIdsHandler;

    private final BookMapper bookMapper;

    public BookService(CreateBookHandler createBookHandler, DeleteBookHandler deleteBookHandler, GetBookHandler getBookHandler, GetAllBooksHandler getAllBooksHandler, UpdateBookHandler updateBookHandler, GetBooksByIdsHandler getBooksByIdsHandler, BookMapper bookMapper) {
        this.createBookHandler = createBookHandler;
        this.deleteBookHandler = deleteBookHandler;
        this.getBookHandler = getBookHandler;
        this.getAllBooksHandler = getAllBooksHandler;
        this.updateBookHandler = updateBookHandler;
        this.getBooksByIdsHandler = getBooksByIdsHandler;
        this.bookMapper = bookMapper;
    }

    public void create(CreateBookRequest createBookRequest) {
        Book book = Book.builder()
                .title( createBookRequest.title() )
                .description( createBookRequest.description() )
                .releaseYear( createBookRequest.releaseYear() )
                .author( createBookRequest.author() )
                .isbn( createBookRequest.isbn() )
                .pageCount( createBookRequest.pageCount() )
                .publisher( createBookRequest.publisher() )
                .genres( createBookRequest.genres() )
                .coverUrl(createBookRequest.coverUrl())
                .build();

        createBookHandler.handler(book);
    }

    public void delete(UUID bookId){
        deleteBookHandler.execute(bookId);
    }

    public GetBookResponse get(UUID bookId){
        Book book = getBookHandler.execute(bookId);

        return bookMapper.toGetResponse(book);
    }

    public List<GetBookResponse> getAll(int pageNumber, int pageSize){
        PageBook pageBook = new PageBook(pageSize, pageNumber);

        Page<Book> bookPage = getAllBooksHandler.execute(pageBook);

        return bookPage.stream().map(bookMapper::toGetResponse).toList();
    }

    public void update(UUID bookId, UpdateBookRequest updateBookRequest) {
        updateBookHandler.execute(bookId, updateBookRequest);
    }


    public List<Recommendation> getByIds(List<UUID> bookIds) {
        List<Book> bookList = getBooksByIdsHandler.execute(bookIds);

        return bookList.stream().map(bookMapper::toFeatureResponse).toList();
    }

}
