package com.vellumhub.engagement_service.module.reading_session_entry.presentation.controller;

import com.vellumhub.engagement_service.module.reading_session_entry.application.command.CreateReadingSessionEntryCommand;
import com.vellumhub.engagement_service.module.reading_session_entry.application.use_case.CreateReadingSessionEntryUseCase;
import com.vellumhub.engagement_service.module.reading_session_entry.presentation.dto.CreateReadingSessionEntryRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/reading-session-entries")
public class ReadingSessionEntryController {

    private final CreateReadingSessionEntryUseCase createReadingSessionEntryUseCase;

    public ReadingSessionEntryController(CreateReadingSessionEntryUseCase createReadingSessionEntryUseCase) {
        this.createReadingSessionEntryUseCase = createReadingSessionEntryUseCase;
    }

    @PostMapping
    public ResponseEntity<Void> create(CreateReadingSessionEntryRequest request){
        var command = CreateReadingSessionEntryCommand.create(request.bookId(), request.type(), request.pageRead());

        createReadingSessionEntryUseCase.execute(command);

        return ResponseEntity.ok().build();
    }

}
