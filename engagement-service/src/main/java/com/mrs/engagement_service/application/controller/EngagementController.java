package com.mrs.engagement_service.application.controller;

import com.mrs.engagement_service.application.dto.GetMediaStatusResponse;
import com.mrs.engagement_service.application.dto.InteractionCreateRequest;
import com.mrs.engagement_service.application.dto.InteractionGetResponse;
import com.mrs.engagement_service.domain.model.InteractionType;
import com.mrs.engagement_service.domain.service.EngagementService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/engagement")
public class EngagementController {

    private final EngagementService engagementService;

    public EngagementController(EngagementService engagementService) {
        this.engagementService = engagementService;
    }

    @PostMapping
    public ResponseEntity<String> create(@RequestBody @Valid InteractionCreateRequest engagement) {
        engagementService.create(engagement);
        return ResponseEntity.status(HttpStatus.CREATED).body("Engagement registered with success");
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<InteractionGetResponse>> findAllOfUser(
            @PathVariable UUID userId,
            @RequestParam(required = false) InteractionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        List<InteractionGetResponse> response = engagementService.findAllOfUser(
                userId, type, from, to, pageNumber, pageSize
        );
        return ResponseEntity.ok(response);
    }


    @GetMapping("/media/{mediaId}/stats")
    public ResponseEntity<GetMediaStatusResponse> getMediaStatus(@PathVariable UUID mediaId) {
        GetMediaStatusResponse response = engagementService.getMediaStatus(mediaId);

        return ResponseEntity.ok(response);
    }

}