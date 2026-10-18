package com.vellumhub.catalog_service.module.book.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.vellumhub.catalog_service.module.book.application.service.BookService;
import com.vellumhub.catalog_service.module.book.presentation.controller.BookController;
import com.vellumhub.catalog_service.share.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "jwt.public-key-location=classpath:security/test-public.pem"
})
@DisplayName("BookController WebMvcTest")
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private BookService bookService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static RequestPostProcessor adminJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor userJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Nested
    @DisplayName("Security - POST /books requires ADMIN role")
    class SecurityTests {

        @Test
        @DisplayName("Should return 403 when authenticated user does not have ADMIN role")
        void shouldReturn403WhenUserNotAdmin() throws Exception {
            String validBody = """
                    {
                        "title": "Valid Title",
                        "description": "Valid Description",
                        "releaseYear": 2020,
                        "coverUrl": "https://example.com/cover.jpg",
                        "author": "Author Name",
                        "isbn": "978-0-000-00000-0",
                        "pageCount": 300,
                        "publisher": "Publisher",
                        "genres": ["FANTASY"]
                    }
                    """;

            mockMvc.perform(post("/books")
                            .with(userJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validBody))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 403 on DELETE when authenticated user does not have ADMIN role")
        void shouldReturn403OnDeleteWhenUserNotAdmin() throws Exception {
            mockMvc.perform(delete("/books/" + UUID.randomUUID())
                            .with(userJwt()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Validation - POST /books returns 400 on invalid input")
    class ValidationTests {

        @Test
        @DisplayName("Should return 400 when title is blank")
        void shouldReturn400WhenTitleIsBlank() throws Exception {
            String invalidBody = """
                    {
                        "title": "",
                        "description": "Valid Description",
                        "releaseYear": 2020,
                        "coverUrl": "https://example.com/cover.jpg",
                        "author": "Author Name",
                        "isbn": "978-0-000-00000-0",
                        "pageCount": 300,
                        "publisher": "Publisher",
                        "genres": ["FANTASY"]
                    }
                    """;

            mockMvc.perform(post("/books")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when genres list is empty")
        void shouldReturn400WhenGenresIsEmpty() throws Exception {
            String invalidBody = """
                    {
                        "title": "Valid Title",
                        "description": "Valid Description",
                        "releaseYear": 2020,
                        "coverUrl": "https://example.com/cover.jpg",
                        "author": "Author Name",
                        "isbn": "978-0-000-00000-0",
                        "pageCount": 300,
                        "publisher": "Publisher",
                        "genres": []
                    }
                    """;

            mockMvc.perform(post("/books")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when author is missing")
        void shouldReturn400WhenAuthorIsMissing() throws Exception {
            String invalidBody = """
                    {
                        "title": "Valid Title",
                        "description": "Valid Description",
                        "releaseYear": 2020,
                        "coverUrl": "https://example.com/cover.jpg",
                        "isbn": "978-0-000-00000-0",
                        "pageCount": 300,
                        "publisher": "Publisher",
                        "genres": ["FANTASY"]
                    }
                    """;

            mockMvc.perform(post("/books")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBody))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Not Found - GET /books/{id} returns 404 when book does not exist")
    class NotFoundTests {

        @Test
        @DisplayName("Should return 404 when book is not found by ID")
        void shouldReturn404WhenBookNotFound() throws Exception {
            UUID bookId = UUID.randomUUID();
            given(bookService.get(any(UUID.class))).willThrow(new BookNotFoundException());

            mockMvc.perform(get("/books/" + bookId)
                            .with(userJwt()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Success - POST /books returns 201 when ADMIN creates book")
    class SuccessTests {

        @Test
        @DisplayName("Should return 201 when ADMIN creates a valid book")
        void shouldReturn201WhenAdminCreatesBook() throws Exception {
            String validBody = """
                    {
                        "title": "Valid Title",
                        "description": "Valid Description",
                        "releaseYear": 2020,
                        "coverUrl": "https://example.com/cover.jpg",
                        "author": "Author Name",
                        "isbn": "978-0-000-00000-0",
                        "pageCount": 300,
                        "publisher": "Publisher",
                        "genres": ["FANTASY"]
                    }
                    """;

            mockMvc.perform(post("/books")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validBody))
                    .andExpect(status().isCreated());
        }
    }
}
