package com.mrs.catalog_service.module.book_progress.application.mapper;

import com.mrs.catalog_service.module.book_progress.application.dto.BookProgressResponse;
import com.mrs.catalog_service.module.book_progress.domain.model.BookProgress;
import org.springframework.stereotype.Component;

@Component
public class BookProgressMapper {

    public BookProgressResponse toResponse(BookProgress bookProgress) {
        return new BookProgressResponse(
                bookProgress.getId(),
                bookProgress.getBookId(),
                bookProgress.getReadingStatus(),
                bookProgress.getCurrentPage()
        );
    }

}
