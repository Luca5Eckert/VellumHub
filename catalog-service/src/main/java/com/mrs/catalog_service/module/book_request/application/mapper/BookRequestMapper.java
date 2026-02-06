package com.mrs.catalog_service.module.book_request.application.mapper;

import com.mrs.catalog_service.module.book_request.application.dto.CreateBookRequestDto;
import com.mrs.catalog_service.module.book_request.domain.command.CreateBookRequestCommand;
import org.springframework.stereotype.Component;

@Component
public class BookRequestMapper {

    public CreateBookRequestCommand toCreateBookRequestCommand(CreateBookRequestDto createBookRequestDto) {
        return new CreateBookRequestCommand(
                createBookRequestDto.title(),
                createBookRequestDto.description(),
                createBookRequestDto.releaseYear(),
                createBookRequestDto.coverUrl(),
                createBookRequestDto.author(),
                createBookRequestDto.isbn(),
                createBookRequestDto.pageCount(),
                createBookRequestDto.publisher(),
                createBookRequestDto.genres()
        );
    }

}
