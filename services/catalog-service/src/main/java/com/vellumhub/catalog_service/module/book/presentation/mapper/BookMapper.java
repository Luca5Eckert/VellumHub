package com.vellumhub.catalog_service.module.book.presentation.mapper;

import com.vellumhub.catalog_service.module.book.domain.model.Genre;
import com.vellumhub.catalog_service.module.book.presentation.dto.GetBookResponse;
import com.vellumhub.catalog_service.module.book.presentation.dto.Recommendation;
import com.vellumhub.catalog_service.module.book.domain.model.Book;
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
                book.getGenres().stream().map(Genre::getName).sorted().toList(),
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
                book.getGenres().stream().map(Genre::getName).sorted().toList(),
                book.getCreatedAt(),
                book.getUpdatedAt()
        );
    }

}
