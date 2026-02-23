package com.mrs.catalog_service.module.book.infrastructure.storage;

import com.mrs.catalog_service.module.book.domain.exception.BookDomainException;
import com.mrs.catalog_service.module.book.domain.port.BookCoverStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

@Component
public class LocalBookCoverStorageAdapter implements BookCoverStorage {

    private final Path baseDir;

    public LocalBookCoverStorageAdapter(
            @Value("${app.upload.books-dir:./uploads/books}") String uploadBooksDir
    ) {
        this.baseDir = Paths.get(uploadBooksDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(UUID bookId, InputStream content, String originalFilename) {
        String ext = resolveExtension(originalFilename);
        String filename = bookId + "-" + UUID.randomUUID() + ext;

        try {
            Files.createDirectories(baseDir);

            Path target = baseDir.resolve(filename).normalize();
            if (!target.startsWith(baseDir)) {
                throw new BookDomainException("Invalid file path");
            }

            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BookDomainException("Failed to store file: " + e.getMessage());
        }

        return "/files/books/" + filename;
    }

    @Override
    public Optional<Resource> load(String filename) {
        try {
            Path filePath = baseDir.resolve(filename).normalize();
            if (!filePath.startsWith(baseDir)) {
                return Optional.empty();
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return Optional.of(resource);
            }
            return Optional.empty();
        } catch (MalformedURLException e) {
            return Optional.empty();
        }
    }

    private String resolveExtension(String originalFilename) {
        if (originalFilename != null && originalFilename.contains(".")) {
            String rawExt = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
            if (rawExt.matches("^(png|jpg|jpeg|webp|gif)$")) {
                return "." + rawExt;
            }
        }
        return "";
    }
}
