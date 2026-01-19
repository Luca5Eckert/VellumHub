package com.mrs.catalog_service.controller;

import com.mrs.catalog_service.dto.CreateMediaRequest;
import com.mrs.catalog_service.dto.GetMediaResponse;
import com.mrs.catalog_service.dto.UpdateMediaRequest; // Import adicionado
import com.mrs.catalog_service.service.MediaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> create(@RequestBody @Valid CreateMediaRequest createMediaRequest){
        mediaService.create(createMediaRequest);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id){
        mediaService.delete(id);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<GetMediaResponse> getById(@PathVariable UUID id) {
        GetMediaResponse mediaResponse = mediaService.get(id);

        return ResponseEntity.ok(mediaResponse);
    }

    @GetMapping
    public ResponseEntity<List<GetMediaResponse>> getAll(
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        List<GetMediaResponse> mediaResponseList = mediaService.getAll(pageNumber, pageSize);

        return ResponseEntity.ok(mediaResponseList);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> update(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateMediaRequest updateMediaRequest
    ) {
        mediaService.update(id, updateMediaRequest);

        return ResponseEntity.ok().build();
    }
}