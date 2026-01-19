package com.mrs.catalog_service.application.dto;


public record PageMedia(
        int pageSize,
        int pageNumber
) {

    public PageMedia {
        pageSize = pageSize < 50 && pageSize > 0 ? pageSize : 10;
        pageNumber = Math.max(pageNumber, 0);
    }

}
