package com.mrs.catalog_service.module.book.application.mapper;

import com.mrs.catalog_service.module.book.application.dto.GetBookResponse;
import com.mrs.catalog_service.module.book.application.dto.Recommendation;
import com.mrs.catalog_service.module.book.domain.model.Book;
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

    public Recommendation toFeatureResponse(Book book) {
        return new Recommendation(
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
