package com.mrs.catalog_service.service;

import com.mrs.catalog_service.module.book.application.command.CreateBookCommand;
import com.mrs.catalog_service.module.book.application.service.BookService;
import com.mrs.catalog_service.module.book.domain.handler.*;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.presentation.dto.*;
import com.mrs.catalog_service.module.book.presentation.mapper.BookMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock private CreateBookHandler createBookHandler;
    @Mock private DeleteBookHandler deleteBookHandler;
    @Mock private GetBookHandler getBookHandler;
    @Mock private GetAllBooksHandler getAllBooksHandler;
    @Mock private UpdateBookHandler updateBookHandler;
    @Mock private GetBooksByIdsHandler getBooksByIdsHandler;
    @Mock private UploadBookCoverHandler uploadBookCoverHandler;
    @Mock private GetBookCoverHandler getBookCoverHandler;
    @Mock private GetBookCoversBulkHandler getBookCoversBulkHandler;
    @Mock private BookMapper bookMapper;

    @InjectMocks
    private BookService bookService;

    @Nested
    @DisplayName("Update & Creation Tests")
    class WriteTests {

        @Test
        @DisplayName("update - should delegate to updateBookHandler")
        void shouldUpdateBook() {
            UUID bookId = UUID.randomUUID();
            UpdateBookRequest request = new UpdateBookRequest(
                    "Title", "Desc", 2024, "url", "Author", "ISBN", 100, "Pub", Set.of("Sci-Fi")
            );

            bookService.update(bookId, request);

            verify(updateBookHandler).execute(bookId, request);
        }

        @Test
        @DisplayName("create - should convert request to command and execute")
        void shouldCreateBook() {
            CreateBookRequest request = new CreateBookRequest(
                    "Title", "Desc", 2024, "url", "Author", "ISBN", 100, "Pub", List.of("Sci-Fi")
            );

            bookService.create(request);

            ArgumentCaptor<CreateBookCommand> captor = ArgumentCaptor.forClass(CreateBookCommand.class);
            verify(createBookHandler).execute(captor.capture());
            assertThat(captor.getValue().title()).isEqualTo("Title");
            assertThat(captor.getValue().genres()).containsExactly("Sci-Fi");
        }
    }

    @Nested
    @DisplayName("Recommendation & Bulk Tests")
    class RetrievalTests {

        @Test
        @DisplayName("getByIds - should return recommendation DTOs")
        void shouldReturnRecommendations() {
            UUID id = UUID.randomUUID();
            List<UUID> ids = List.of(id);
            Book mockBook = mock(Book.class);
            Instant now = Instant.now();

            // Ajustado para o novo Record Recommendation
            Recommendation rec = new Recommendation(
                    id, "Title", "Desc", 2024, "url", List.of("Sci-Fi"), now, now
            );

            when(getBooksByIdsHandler.execute(ids)).thenReturn(List.of(mockBook));
            when(bookMapper.toFeatureResponse(mockBook)).thenReturn(rec);

            List<Recommendation> result = bookService.getByIds(ids);

            assertThat(result).hasSize(1).containsExactly(rec);
        }

        @Test
        @DisplayName("getBookCoversBulk - should return bulk encoded covers")
        void shouldReturnBulkCovers() {
            UUID id = UUID.randomUUID();
            List<UUID> ids = List.of(id);

            // Ajustado para o novo Record BookCoverResponse (bookId, coverData, contentType)
            BookCoverResponse coverResponse = new BookCoverResponse(id, "base64String", "image/jpeg");

            when(getBookCoversBulkHandler.execute(ids)).thenReturn(List.of(coverResponse));

            List<BookCoverResponse> result = bookService.getBookCoversBulk(ids);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).contentType()).isEqualTo("image/jpeg");
        }
    }

    @Nested
    @DisplayName("Cover Upload Tests")
    class CoverUploadTests {

        @Test
        @DisplayName("uploadCover - should execute upload handler with file stream")
        void shouldUploadCover() throws IOException {
            UUID id = UUID.randomUUID();
            MultipartFile file = mock(MultipartFile.class);
            InputStream is = mock(InputStream.class);

            when(file.isEmpty()).thenReturn(false);
            when(file.getInputStream()).thenReturn(is);
            when(file.getOriginalFilename()).thenReturn("img.png");
            when(file.getContentType()).thenReturn("image/png");
            when(uploadBookCoverHandler.execute(eq(id), any(), eq("img.png"), eq("image/png")))
                    .thenReturn("http://storage.com/img.png");

            String url = bookService.uploadCover(id, file);

            assertThat(url).isEqualTo("http://storage.com/img.png");
        }
    }
}