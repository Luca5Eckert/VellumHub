package com.vellumhub.catalog_service.module.book.infrastructure.provider.google.adapter;

import com.vellumhub.catalog_service.module.book.domain.exception.ExternalBookProviderException;
import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.model.ExternalBook;
import com.vellumhub.catalog_service.module.book.domain.model.ExternalBookSearchField;
import com.vellumhub.catalog_service.module.book.domain.port.BookExternalProvider;
import com.vellumhub.catalog_service.module.book.infrastructure.provider.google.dto.GoogleBookItem;
import com.vellumhub.catalog_service.module.book.infrastructure.provider.google.dto.GoogleBooksResponse;
import com.vellumhub.catalog_service.module.book.infrastructure.provider.google.dto.IndustryIdentifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
public class GoogleBooksAdapter implements BookExternalProvider {

    private final RestClient restClient;

    public GoogleBooksAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${app.integration.google-books.url:https://www.googleapis.com/books/v1}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public Optional<Book> fetchByIsbn(String isbn) {
        try {
            GoogleBooksResponse response = restClient.get()
                    .uri("/volumes?q=isbn:{isbn}", isbn)
                    .retrieve().body(GoogleBooksResponse.class);

            return items(response).stream().findFirst().map(item -> mapVolume(item, isbn))
                    .map(volume -> Book.create(volume.title(), volume.description(),
                            Optional.ofNullable(volume.releaseYear()).orElse(0), volume.author(), isbn,
                            Optional.ofNullable(volume.pageCount()).orElse(0), volume.publisher(),
                            volume.coverUrl(), Set.of()));
        } catch (RestClientException failure) {
            throw unavailable(failure);
        }
    }

    @Override
    public List<ExternalBook> search(String query, ExternalBookSearchField field) {
        String prefix = switch (field) {
            case ALL -> "";
            case TITLE -> "intitle:";
            case AUTHOR -> "inauthor:";
            case ISBN -> "isbn:";
        };
        try {
            GoogleBooksResponse response = restClient.get()
                    .uri("/volumes?q={query}&maxResults=10", prefix + query)
                    .retrieve().body(GoogleBooksResponse.class);
            return items(response).stream().filter(item -> item.id() != null && !item.id().isBlank())
                    .limit(10).map(item -> mapVolume(item, null)).toList();
        } catch (RestClientException failure) {
            throw unavailable(failure);
        }
    }

    @Override
    public Optional<ExternalBook> fetchVolume(String volumeId) {
        try {
            GoogleBookItem volume = restClient.get()
                    .uri("/volumes/{volumeId}", volumeId)
                    .retrieve().body(GoogleBookItem.class);
            if (volume == null || volume.volumeInfo() == null || !volumeId.equals(volume.id())) {
                return Optional.empty();
            }
            return Optional.of(mapVolume(volume, null));
        } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        } catch (RestClientException failure) {
            throw unavailable(failure);
        }
    }

    private List<GoogleBookItem> items(GoogleBooksResponse response) {
        return response == null || response.isEmpty() ? List.of()
                : response.items().stream().filter(Objects::nonNull).filter(item -> item.volumeInfo() != null).toList();
    }

    private ExternalBook mapVolume(GoogleBookItem item, String requestedIsbn) {
        var info = item.volumeInfo();
        String author = info.authors() == null ? "" : String.join(", ", info.authors());
        return new ExternalBook(item.id(), limited(info.title(), "Title Unavailable", 255),
                limited(author, "Unknown Author", 255), limited(info.description(), null, 1000),
                extractYear(info.publishedDate()), info.pageCount(),
                requestedIsbn == null ? isbn(info.industryIdentifiers()) : requestedIsbn,
                limited(info.publisher(), "Unknown Publisher", 255),
                coverUrl(info.imageLinks() == null ? null : info.imageLinks().thumbnail()));
    }

    private String isbn(List<IndustryIdentifier> identifiers) {
        if (identifiers == null) {
            return null;
        }
        return identifiers.stream().filter(Objects::nonNull)
                .filter(id -> id.identifier() != null)
                .filter(id -> ("ISBN_13".equals(id.type()) && id.identifier().matches("[0-9]{13}"))
                        || ("ISBN_10".equals(id.type()) && id.identifier().matches("[0-9]{9}[0-9X]")))
                .map(IndustryIdentifier::identifier)
                .max(Comparator.comparingInt(String::length)).orElse(null);
    }

    private Integer extractYear(String publishedDate) {
        if (publishedDate == null || !publishedDate.matches("[0-9]{4}(-[0-9]{2}(-[0-9]{2})?)?")) {
            return null;
        }
        return Integer.valueOf(publishedDate.substring(0, 4));
    }

    private String coverUrl(String value) {
        if (value == null || value.length() > 255) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null && ("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme())) ? value : null;
        } catch (IllegalArgumentException invalidUrl) {
            return null;
        }
    }

    private String limited(String value, String fallback, int limit) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        int length = value.codePointCount(0, value.length());
        return length <= limit ? value : value.substring(0, value.offsetByCodePoints(0, limit));
    }

    private ExternalBookProviderException unavailable(RestClientException failure) {
        log.warn("Google Books request failed: {}", failure.getClass().getSimpleName());
        return new ExternalBookProviderException(failure);
    }
}
