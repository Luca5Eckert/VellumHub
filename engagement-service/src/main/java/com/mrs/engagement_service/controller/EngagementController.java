package com.mrs.engagement_service.controller;

import com.mrs.engagement_service.dto.InteractionCreateRequest;
import com.mrs.engagement_service.dto.InteractionGetResponse;
import com.mrs.engagement_service.model.InteractionType;
import com.mrs.engagement_service.service.EngagementService;
import jakarta.validation.Valid;
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
    public ResponseEntity<String> create(@RequestBody @Valid InteractionCreateRequest engagement){
        engagementService.create(engagement);

        return ResponseEntity.status(HttpStatus.CREATED).body("Engagement registered with success");
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<InteractionGetResponse>> findAllOfUser(
            @PathVariable UUID userId,
            @RequestParam(required = false) InteractionType type,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        List<InteractionGetResponse> response = engagementService.findAllOfUser(
                userId, type, from, to, pageNumber, pageSize
        );

        return ResponseEntity.ok(response);
    }

}
