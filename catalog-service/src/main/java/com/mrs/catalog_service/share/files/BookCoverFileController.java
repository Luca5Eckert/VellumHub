package com.mrs.catalog_service.share.files;

import com.mrs.catalog_service.module.book.domain.port.BookCoverStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/files/books")
@Tag(name = "Files", description = "Endpoints for serving static files")
public class BookCoverFileController {

    private final BookCoverStorage bookCoverStorage;

    public BookCoverFileController(BookCoverStorage bookCoverStorage) {
        this.bookCoverStorage = bookCoverStorage;
    }

    @GetMapping("/{filename}")
    @Operation(summary = "Serve book cover image", description = "Returns the cover image for a book by filename")
    public ResponseEntity<Resource> serveFile(
            @Parameter(description = "Image filename") @PathVariable String filename
    ) {
        Optional<Resource> resource = bookCoverStorage.load(filename);

        if (resource.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String contentType = resolveContentType(filename);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(resource.get());
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
