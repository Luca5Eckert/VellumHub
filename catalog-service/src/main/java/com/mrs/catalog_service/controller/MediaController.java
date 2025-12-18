package com.mrs.catalog_service.controller;

import com.mrs.catalog_service.dto.CreateMediaRequest;
import com.mrs.catalog_service.dto.GetMediaResponse;
import com.mrs.catalog_service.service.MediaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping
    @PreAuthorize("ADMIN")
    public ResponseEntity<String> create(@RequestBody @Valid CreateMediaRequest createMediaRequest){
        mediaService.create(createMediaRequest);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping
    @PreAuthorize("ADMIN")
    public ResponseEntity<Void> delete(@RequestBody UUID mediaId){
        mediaService.delete(mediaId);

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<GetMediaResponse> getById(@PathVariable UUID id) {
        GetMediaResponse mediaResponse = mediaService.get(id);
        return ResponseEntity.ok(mediaResponse);
    }

}
