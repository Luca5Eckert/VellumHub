package com.vellumhub.catalog_service.module.book_progress.application.mapper;

import com.vellumhub.catalog_service.module.book_progress.application.dto.BookProgressResponse;
import com.vellumhub.catalog_service.module.book_progress.domain.model.BookProgress;
import org.springframework.stereotype.Component;

@Component
public class BookProgressMapper {

    public BookProgressResponse toResponse(BookProgress bookProgress) {
        return new BookProgressResponse(
                bookProgress.getId(),
                bookProgress.getBook().getId(),
                bookProgress.getReadingStatus(),
                bookProgress.getCurrentPage()
        );
    }

}
