package com.mrs.catalog_service.module.book.domain.service;

import com.mrs.catalog_service.module.book.application.dto.*;
import com.mrs.catalog_service.module.book.application.mapper.BookMapper;
import com.mrs.catalog_service.module.book.domain.exception.BookDomainException;
import com.mrs.catalog_service.module.book.domain.handler.*;
import com.mrs.catalog_service.module.book.domain.model.Book;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    private final UploadBookCoverHandler uploadBookCoverHandler;
    private final GetBookCoverHandler getBookCoverHandler;
    private final GetBookCoversBulkHandler getBookCoversBulkHandler;

    private final BookMapper bookMapper;

    public BookService(CreateBookHandler createBookHandler, DeleteBookHandler deleteBookHandler, GetBookHandler getBookHandler, GetAllBooksHandler getAllBooksHandler, UpdateBookHandler updateBookHandler, GetBooksByIdsHandler getBooksByIdsHandler, UploadBookCoverHandler uploadBookCoverHandler, GetBookCoverHandler getBookCoverHandler, GetBookCoversBulkHandler getBookCoversBulkHandler, BookMapper bookMapper) {
        this.createBookHandler = createBookHandler;
        this.deleteBookHandler = deleteBookHandler;
        this.getBookHandler = getBookHandler;
        this.getAllBooksHandler = getAllBooksHandler;
        this.updateBookHandler = updateBookHandler;
        this.getBooksByIdsHandler = getBooksByIdsHandler;
        this.uploadBookCoverHandler = uploadBookCoverHandler;
        this.getBookCoverHandler = getBookCoverHandler;
        this.getBookCoversBulkHandler = getBookCoversBulkHandler;
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

    public String uploadCover(UUID bookId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BookDomainException("File must not be empty");
        }

        try {
            return uploadBookCoverHandler.execute(
                    bookId,
                    file.getInputStream(),
                    file.getOriginalFilename(),
                    file.getContentType()
            );
        } catch (IOException e) {
            throw new BookDomainException("Failed to read uploaded file: " + e.getMessage());
        }
    }

    /**
     * Retrieves the cover image for a book by its ID.
     *
     * @param bookId the ID of the book
     * @return the cover image as a Resource
     */
    public Resource getBookCover(UUID bookId) {
        return getBookCoverHandler.execute(bookId);
    }

    /**
     * Retrieves cover images for multiple books in a single operation.
     * Solves N+1 problem when fetching covers for recommendations.
     *
     * @param bookIds list of book IDs to retrieve covers for
     * @return list of BookCoverResponse with Base64 encoded cover data
     */
    public List<BookCoverResponse> getBookCoversBulk(List<UUID> bookIds) {
        return getBookCoversBulkHandler.execute(bookIds);
    }

}
