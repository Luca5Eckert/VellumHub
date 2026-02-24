package com.mrs.catalog_service.module.book.application.controller;

import com.mrs.catalog_service.module.book.application.dto.CreateBookRequest;
import com.mrs.catalog_service.module.book.application.dto.GetBookResponse;
import com.mrs.catalog_service.module.book.application.dto.Recommendation;
import com.mrs.catalog_service.module.book.application.dto.UpdateBookRequest;
import com.mrs.catalog_service.module.book.domain.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/books")
@Tag(name = "Books", description = "Endpoints for book catalog management and CRUD operations")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create new book", description = "Creates a new book in the catalog. Requires ADMIN role.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Book created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid data", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin permission required", content = @Content)
    })
    public ResponseEntity<Void> create(@RequestBody @Valid CreateBookRequest createBookRequest){
        bookService.create(createBookRequest);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete book", description = "Removes a book from the catalog. Requires ADMIN role.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Book deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin permission required", content = @Content),
            @ApiResponse(responseCode = "404", description = "Book not found", content = @Content)
    })
    public ResponseEntity<Void> delete(@Parameter(description = "Book ID") @PathVariable UUID id){
        bookService.delete(id);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Find book by ID", description = "Returns the details of a specific book")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Book found",
                    content = @Content(schema = @Schema(implementation = GetBookResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Book not found", content = @Content)
    })
    public ResponseEntity<GetBookResponse> getById(@Parameter(description = "Book ID") @PathVariable UUID id) {
        GetBookResponse bookResponse = bookService.get(id);

        return ResponseEntity.ok(bookResponse);
    }

    @GetMapping
    @Operation(summary = "List all books", description = "Returns a paginated list of all books in the catalog")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of books returned successfully",
                    content = @Content(schema = @Schema(implementation = GetBookResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public ResponseEntity<List<GetBookResponse>> getAll(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int pageNumber,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int pageSize
    ) {
        List<GetBookResponse> bookResponseList = bookService.getAll(pageNumber, pageSize);

        return ResponseEntity.ok(bookResponseList);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update book", description = "Updates the data of an existing book. Requires ADMIN role.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Book updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid data", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin permission required", content = @Content),
            @ApiResponse(responseCode = "404", description = "Book not found", content = @Content)
    })
    public ResponseEntity<Void> update(
            @Parameter(description = "Book ID") @PathVariable UUID id,
            @RequestBody @Valid UpdateBookRequest updateBookRequest
    ) {
        bookService.update(id, updateBookRequest);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/bulk")
    @Operation(summary = "Get multiple books by IDs", description = "Retrieves book details for multiple books in a single request. Used internally by the Recommendation Service for enrichment.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Books retrieved successfully",
                    content = @Content(schema = @Schema(implementation = Recommendation.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content)
    })
    public ResponseEntity<List<Recommendation>> getByIds(@RequestBody List<UUID> bookIds) {
        List<Recommendation> recommendations = bookService.getByIds(bookIds);

        return ResponseEntity.ok(recommendations);
    }

    @PostMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Upload book cover", description = "Uploads a cover image for the specified book. Requires ADMIN role.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cover uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin permission required", content = @Content),
            @ApiResponse(responseCode = "404", description = "Book not found", content = @Content)
    })
    public ResponseEntity<Map<String, String>> uploadCover(
            @Parameter(description = "Book ID") @PathVariable UUID id,
            @RequestParam("file") MultipartFile file
    ) {
        String coverUrl = bookService.uploadCover(id, file);
        return ResponseEntity.ok(Map.of("coverUrl", coverUrl));
    }

    @GetMapping(value = "/{id}/cover")
    @Operation(summary = "Get book cover image", description = "Retrieves the cover image for the specified book by its ID")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cover image retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Book has no cover image", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Book not found or cover file not found", content = @Content)
    })
    public ResponseEntity<Resource> getBookCover(
            @Parameter(description = "Book ID") @PathVariable UUID id
    ) {
        Resource coverImage = bookService.getBookCover(id);
        String filename = coverImage.getFilename() != null ? coverImage.getFilename() : "";
        String contentType = resolveContentType(filename);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(coverImage);
    }

    private String resolveContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}