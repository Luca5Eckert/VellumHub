package com.mrs.catalog_service.module.book.domain.port;

import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

public interface BookCoverStorage {

    /**
     * Stores the given image stream and returns the public URL for the cover.
     *
     * @param bookId           the book the cover belongs to
     * @param content          raw image bytes
     * @param originalFilename original filename (used only to extract the extension)
     * @return the public URL path, e.g. {@code /files/books/<filename>}
     */
    String store(UUID bookId, InputStream content, String originalFilename);

    /**
     * Loads the stored cover image as a {@link Resource}.
     *
     * @param filename the filename returned by a previous {@link #store} call
     * @return the resource if it exists and is readable, otherwise empty
     */
    Optional<Resource> load(String filename);
}
