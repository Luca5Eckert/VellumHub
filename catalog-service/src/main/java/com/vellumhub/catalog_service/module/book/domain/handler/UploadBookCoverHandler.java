package com.mrs.catalog_service.module.book.domain.handler;

import com.mrs.catalog_service.module.book.domain.exception.BookDomainException;
import com.mrs.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.port.BookCoverStorage;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.UUID;

@Component
public class UploadBookCoverHandler {

    private final BookCoverStorage bookCoverStorage;
    private final BookRepository bookRepository;

    public UploadBookCoverHandler(BookCoverStorage bookCoverStorage, BookRepository bookRepository) {
        this.bookCoverStorage = bookCoverStorage;
        this.bookRepository = bookRepository;
    }

    @Transactional
    public String execute(UUID bookId, InputStream content, String originalFilename, String contentType) {
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BookDomainException("File must be an image");
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId.toString()));

        String coverUrl = bookCoverStorage.store(bookId, content, originalFilename);

        book.updateCoverUrl(coverUrl);
        bookRepository.save(book);

        return coverUrl;
    }
}
