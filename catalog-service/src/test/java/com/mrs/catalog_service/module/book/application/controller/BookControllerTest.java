package com.mrs.catalog_service.module.book.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrs.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.mrs.catalog_service.module.book.domain.service.BookService;
import com.mrs.catalog_service.share.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2Vz"
})
@DisplayName("BookController WebMvcTest")
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookService bookService;

    @Nested
    @DisplayName("Security - POST /books requires ADMIN role")
    class SecurityTests {

        @Test
        @DisplayName("Should return 403 when authenticated user does not have ADMIN role")
        @WithMockUser(roles = "USER")
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
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validBody))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 403 on DELETE when authenticated user does not have ADMIN role")
        @WithMockUser(roles = "USER")
        void shouldReturn403OnDeleteWhenUserNotAdmin() throws Exception {
            mockMvc.perform(delete("/books/" + UUID.randomUUID()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Validation - POST /books returns 400 on invalid input")
    class ValidationTests {

        @Test
        @DisplayName("Should return 400 when title is blank")
        @WithMockUser(roles = "ADMIN")
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
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when genres list is empty")
        @WithMockUser(roles = "ADMIN")
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
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when author is missing")
        @WithMockUser(roles = "ADMIN")
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
        @WithMockUser(roles = "USER")
        void shouldReturn404WhenBookNotFound() throws Exception {
            UUID bookId = UUID.randomUUID();
            given(bookService.get(any(UUID.class))).willThrow(new BookNotFoundException());

            mockMvc.perform(get("/books/" + bookId))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Success - POST /books returns 201 when ADMIN creates book")
    class SuccessTests {

        @Test
        @DisplayName("Should return 201 when ADMIN creates a valid book")
        @WithMockUser(roles = "ADMIN")
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
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validBody))
                    .andExpect(status().isCreated());
        }
    }
}
