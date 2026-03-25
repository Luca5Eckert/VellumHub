package com.mrs.catalog_service.module.book.infrastructure.provider.google.adapter;

import com.mrs.catalog_service.module.book.domain.model.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleBooksAdapterTest {

    private MockRestServiceServer mockServer;
    private GoogleBooksAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        this.mockServer = MockRestServiceServer.bindTo(builder).build();
        this.adapter = new GoogleBooksAdapter(builder, "https://api.google.com");
    }

    @Test
    void shouldReturnBookWhenApiReturnsValidData() {
        String isbn = "9780132350884";
        String jsonResponse = """
                {
                  "items": [
                    {
                      "volumeInfo": {
                        "title": "Clean Code",
                        "authors": ["Robert C. Martin"],
                        "publishedDate": "2008-08-11",
                        "description": "Even bad code can function.",
                        "pageCount": 464,
                        "publisher": "Prentice Hall",
                        "imageLinks": {
                          "thumbnail": "http://example.com/cover.jpg"
                        }
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://api.google.com/volumes?q=isbn:" + isbn))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        Optional<Book> result = adapter.fetchByIsbn(isbn);

        assertThat(result).isPresent();
        Book book = result.get();
        assertThat(book.getTitle()).isEqualTo("Clean Code");
        assertThat(book.getAuthor()).isEqualTo("Robert C. Martin");
        assertThat(book.getReleaseYear()).isEqualTo(2008);
        assertThat(book.getPageCount()).isEqualTo(464);
        assertThat(book.getPublisher()).isEqualTo("Prentice Hall");
        assertThat(book.getCoverUrl()).isEqualTo("http://example.com/cover.jpg");
        assertThat(book.getIsbn()).isEqualTo(isbn);

        mockServer.verify();
    }

    @Test
    void shouldReturnBookWithFallbackValuesWhenApiReturnsIncompleteData() {
        String isbn = "1234567890";
        String incompleteJsonResponse = """
                {
                  "items": [
                    {
                      "volumeInfo": {
                        "publishedDate": "invalid-date-format"
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://api.google.com/volumes?q=isbn:" + isbn))
                .andRespond(withSuccess(incompleteJsonResponse, MediaType.APPLICATION_JSON));

        Optional<Book> result = adapter.fetchByIsbn(isbn);

        assertThat(result).isPresent();
        Book book = result.get();
        assertThat(book.getTitle()).isEqualTo("Title Unavailable");
        assertThat(book.getAuthor()).isEqualTo("Unknown Author");
        assertThat(book.getReleaseYear()).isZero();
        assertThat(book.getPageCount()).isZero();
        assertThat(book.getPublisher()).isEqualTo("Unknown Publisher");
        assertThat(book.getCoverUrl()).isNull();

        mockServer.verify();
    }

    @Test
    void shouldReturnEmptyWhenApiReturnsNoItems() {
        String isbn = "0000000000";
        String emptyJsonResponse = """
                {
                  "totalItems": 0,
                  "items": []
                }
                """;

        mockServer.expect(requestTo("https://api.google.com/volumes?q=isbn:" + isbn))
                .andRespond(withSuccess(emptyJsonResponse, MediaType.APPLICATION_JSON));

        Optional<Book> result = adapter.fetchByIsbn(isbn);

        assertThat(result).isEmpty();

        mockServer.verify();
    }

    @Test
    void shouldThrowExceptionWhenApiReturnsError() {
        String isbn = "9999999999";

        mockServer.expect(requestTo("https://api.google.com/volumes?q=isbn:" + isbn))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> adapter.fetchByIsbn(isbn))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("External catalog service is currently unavailable");

        mockServer.verify();
    }
}