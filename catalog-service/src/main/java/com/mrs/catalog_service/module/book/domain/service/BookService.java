package com.mrs.catalog_service.module.book.domain.service;

import com.mrs.catalog_service.module.book.application.dto.*;
import com.mrs.catalog_service.module.book.application.mapper.BookMapper;
import com.mrs.catalog_service.module.book.domain.exception.BookDomainException;
import com.mrs.catalog_service.module.book.domain.handler.*;
import com.mrs.catalog_service.module.book.domain.model.Book;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final UpdateBookCoverHandler updateBookCoverHandler;

    private final BookMapper bookMapper;

    @Value("${app.upload.books-dir:./uploads/books}")
    private String uploadBooksDir;

    public BookService(CreateBookHandler createBookHandler, DeleteBookHandler deleteBookHandler, GetBookHandler getBookHandler, GetAllBooksHandler getAllBooksHandler, UpdateBookHandler updateBookHandler, GetBooksByIdsHandler getBooksByIdsHandler, UpdateBookCoverHandler updateBookCoverHandler, BookMapper bookMapper) {
        this.createBookHandler = createBookHandler;
        this.deleteBookHandler = deleteBookHandler;
        this.getBookHandler = getBookHandler;
        this.getAllBooksHandler = getAllBooksHandler;
        this.updateBookHandler = updateBookHandler;
        this.getBooksByIdsHandler = getBooksByIdsHandler;
        this.updateBookCoverHandler = updateBookCoverHandler;
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

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BookDomainException("File must be an image");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            String rawExt = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
            if (rawExt.matches("^(png|jpg|jpeg|webp|gif)$")) {
                ext = "." + rawExt;
            }
        }

        String filename = bookId + "-" + UUID.randomUUID() + ext;

        try {
            Path baseDir = Paths.get(uploadBooksDir).toAbsolutePath().normalize();
            Files.createDirectories(baseDir);

            Path target = baseDir.resolve(filename).normalize();
            if (!target.startsWith(baseDir)) {
                throw new BookDomainException("Invalid file path");
            }

            Files.copy(file.getInputStream(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BookDomainException("Failed to store file: " + e.getMessage());
        }

        String coverUrl = "/files/books/" + filename;
        updateBookCoverHandler.execute(bookId, coverUrl);
        return coverUrl;
    }

}
