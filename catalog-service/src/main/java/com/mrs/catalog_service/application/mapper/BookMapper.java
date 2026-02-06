package com.mrs.catalog_service.application.mapper;

import com.mrs.catalog_service.application.dto.GetBookResponse;
import com.mrs.catalog_service.application.dto.BookFeatureResponse;
import com.mrs.catalog_service.domain.model.Book;
import org.springframework.stereotype.Component;

@Component
public class BookMapper {

    public GetBookResponse toGetResponse(Book book){
        return new GetBookResponse(
                book.getId(),
                book.getTitle(),
                book.getDescription(),
                book.getReleaseYear(),
                book.getCoverUrl(),
                book.getAuthor(),
                book.getIsbn(),
                book.getPageCount(),
                book.getPublisher(),
                book.getGenres(),
                book.getCreatedAt(),
                book.getUpdatedAt()
        );

    }

    public BookFeatureResponse toFeatureResponse(Book book) {
        return new BookFeatureResponse(
                book.getId(),
                book.getTitle(),
                book.getDescription(),
                book.getReleaseYear(),
                book.getCoverUrl(),
                book.getGenres(),
                book.getCreatedAt(),
                book.getUpdatedAt()
        );
    }

}
