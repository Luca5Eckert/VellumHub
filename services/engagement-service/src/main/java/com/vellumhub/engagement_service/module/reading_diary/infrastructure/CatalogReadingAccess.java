package com.vellumhub.engagement_service.module.reading_diary.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Component
public class CatalogReadingAccess {
    private final RestClient catalog;

    public CatalogReadingAccess(@Value("${app.reading-diary.catalog-url:http://catalog-service:8080}") String url) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        catalog = RestClient.builder().requestFactory(factory).baseUrl(url).build();
    }

    public int pageCount(UUID readingId, String accessToken) {
        try {
            var book = catalog.get().uri("/book-progress/readings/{readingId}/book", readingId)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve().body(ReadingBook.class);
            if (book != null) {
                return book.pageCount();
            }
        } catch (RestClientResponseException response) {
            if (response.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reading not found");
            }
            if (response.getStatusCode().value() == 403) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Catalog reading scope required");
            }
        } catch (RestClientException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog is unavailable");
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog is unavailable");
    }

    private record ReadingBook(int pageCount) {}
}
