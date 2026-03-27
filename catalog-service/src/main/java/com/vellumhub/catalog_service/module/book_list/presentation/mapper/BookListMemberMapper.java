package com.vellumhub.catalog_service.module.book_list.presentation.mapper;

import com.vellumhub.catalog_service.module.book_list.domain.model.BookListMembership;
import com.vellumhub.catalog_service.module.book_list.presentation.dto.response.member.GetMembershipResponse;

import org.springframework.stereotype.Component;

@Component
public class BookListMemberMapper {
    public GetMembershipResponse toMembershipResponse(BookListMembership membership) {
        return new GetMembershipResponse(
                membership.getId(),
                membership.getBookList().getId(),
                membership.getUserId(),
                membership.getRole(),
                membership.getCreatedAt(),
                membership.getUpdatedAt()
        );
    }
}

