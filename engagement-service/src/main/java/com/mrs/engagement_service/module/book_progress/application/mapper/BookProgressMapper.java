package com.mrs.engagement_service.module.book_progress.application.mapper;

import com.mrs.engagement_service.module.book_progress.application.dto.BookProgressResponse;
import com.mrs.engagement_service.module.book_progress.domain.model.ReadingStatus;
import com.mrs.engagement_service.module.rating.domain.model.BookProgress;
import org.springframework.stereotype.Component;

import java.util.UUID;

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
