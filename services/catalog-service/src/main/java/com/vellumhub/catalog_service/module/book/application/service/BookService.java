package com.vellumhub.catalog_service.module.book.application.service;

import com.vellumhub.catalog_service.module.book.application.command.CreateBookCommand;
import com.vellumhub.catalog_service.module.book.application.command.UpdateBookCommand;
import com.vellumhub.catalog_service.module.book.application.handler.DeleteBookHandler;
import com.vellumhub.catalog_service.module.book.application.handler.UploadBookCoverHandler;
import com.vellumhub.catalog_service.module.book.application.handler.create.CreateBookHandler;
import com.vellumhub.catalog_service.module.book.application.handler.create.CreateBookWithIsbnHandler;
import com.vellumhub.catalog_service.module.book.application.handler.get.GetAllBooksHandler;
import com.vellumhub.catalog_service.module.book.application.handler.get.GetBookCoverHandler;
import com.vellumhub.catalog_service.module.book.application.handler.get.GetBookCoversBulkHandler;
import com.vellumhub.catalog_service.module.book.application.handler.get.GetBookHandler;
import com.vellumhub.catalog_service.module.book.application.handler.get.GetBooksByIdsHandler;
import com.vellumhub.catalog_service.module.book.application.handler.update.UpdateBookHandler;
import com.vellumhub.catalog_service.module.book.application.query.PageBook;
import com.vellumhub.catalog_service.module.book.domain.exception.BookDomainException;
import com.vellumhub.catalog_service.module.book.presentation.dto.BookCoverResponse;
import com.vellumhub.catalog_service.module.book.presentation.dto.CreateBookRequest;
import com.vellumhub.catalog_service.module.book.presentation.dto.GetBookResponse;
import com.vellumhub.catalog_service.module.book.presentation.dto.Recommendation;
import com.vellumhub.catalog_service.module.book.presentation.dto.UpdateBookRequest;
import com.vellumhub.catalog_service.module.book.presentation.mapper.BookMapper;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Facade used by {@code BookController}: translates HTTP DTOs into application commands and
 * maps domain results back to response DTOs. Handlers never see presentation types.
 */
@Service
public class BookService {

    private final CreateBookHandler createBookHandler;
    private final CreateBookWithIsbnHandler createBookWithIsbnHandler;
    private final DeleteBookHandler deleteBookHandler;
    private final GetBookHandler getBookHandler;
    private final GetAllBooksHandler getAllBooksHandler;
    private final GetBooksByIdsHandler getBooksByIdsHandler;
    private final UpdateBookHandler updateBookHandler;
    private final UploadBookCoverHandler uploadBookCoverHandler;
    private final GetBookCoverHandler getBookCoverHandler;
    private final GetBookCoversBulkHandler getBookCoversBulkHandler;
    private final BookMapper bookMapper;

    public BookService(
            CreateBookHandler createBookHandler,
            DeleteBookHandler deleteBookHandler,
            GetBookHandler getBookHandler,
            GetAllBooksHandler getAllBooksHandler,
            UpdateBookHandler updateBookHandler,
            GetBooksByIdsHandler getBooksByIdsHandler,
            UploadBookCoverHandler uploadBookCoverHandler,
            GetBookCoverHandler getBookCoverHandler,
            GetBookCoversBulkHandler getBookCoversBulkHandler,
            CreateBookWithIsbnHandler createBookWithIsbnHandler,
            BookMapper bookMapper) {
        this.createBookHandler = createBookHandler;
        this.deleteBookHandler = deleteBookHandler;
        this.getBookHandler = getBookHandler;
        this.getAllBooksHandler = getAllBooksHandler;
        this.updateBookHandler = updateBookHandler;
        this.getBooksByIdsHandler = getBooksByIdsHandler;
        this.uploadBookCoverHandler = uploadBookCoverHandler;
        this.getBookCoverHandler = getBookCoverHandler;
        this.getBookCoversBulkHandler = getBookCoversBulkHandler;
        this.createBookWithIsbnHandler = createBookWithIsbnHandler;
        this.bookMapper = bookMapper;
    }

    public void create(CreateBookRequest request) {
        createBookHandler.execute(new CreateBookCommand(
                request.title(),
                request.description(),
                request.releaseYear(),
                request.author(),
                request.isbn(),
                request.pageCount(),
                request.publisher(),
                request.coverUrl(),
                request.genres() != null ? new HashSet<>(request.genres()) : new HashSet<>()
        ));
    }

    public UUID createByIsbn(String isbn) {
        return createBookWithIsbnHandler.handle(isbn);
    }

    public void delete(UUID bookId) {
        deleteBookHandler.execute(bookId);
    }

    public GetBookResponse get(UUID bookId) {
        return bookMapper.toGetResponse(getBookHandler.execute(bookId));
    }

    public List<GetBookResponse> getAll(int pageNumber, int pageSize) {
        return getAllBooksHandler.execute(new PageBook(pageSize, pageNumber))
                .stream()
                .map(bookMapper::toGetResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GetBookResponse> search(String text, PageBook page) {
        return getAllBooksHandler.search(text, page).stream()
                .map(bookMapper::toGetResponse)
                .toList();
    }

    public List<Recommendation> getByIds(List<UUID> bookIds) {
        return getBooksByIdsHandler.execute(bookIds).stream()
                .map(bookMapper::toFeatureResponse)
                .toList();
    }

    public void update(UUID bookId, UpdateBookRequest request) {
        updateBookHandler.execute(bookId, new UpdateBookCommand(
                request.title(),
                request.description(),
                request.releaseYear(),
                request.coverUrl(),
                request.author(),
                request.isbn(),
                request.pageCount(),
                request.publisher(),
                request.genres()
        ));
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

    public Resource getBookCover(UUID bookId) {
        return getBookCoverHandler.execute(bookId);
    }

    /**
     * Retrieves cover images for multiple books in one call, avoiding N+1 requests from
     * recommendation and list views.
     */
    public List<BookCoverResponse> getBookCoversBulk(List<UUID> bookIds) {
        return getBookCoversBulkHandler.execute(bookIds).stream()
                .map(cover -> new BookCoverResponse(cover.bookId(), cover.coverData(), cover.contentType()))
                .toList();
    }
}
