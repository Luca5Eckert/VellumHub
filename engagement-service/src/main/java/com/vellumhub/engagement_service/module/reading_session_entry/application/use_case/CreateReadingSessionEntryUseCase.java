package com.vellumhub.engagement_service.module.reading_session_entry.application.use_case;

import com.vellumhub.engagement_service.module.reading_session_entry.application.command.CreateReadingSessionEntryCommand;
import com.vellumhub.engagement_service.module.reading_session_entry.domain.port.ReadingSessionEntryRepository;
import org.springframework.stereotype.Service;

@Service
public class CreateReadingSessionEntryUseCase {

    private final ReadingSessionEntryRepository readingSessionEntryRepository;

    public CreateReadingSessionEntryUseCase(ReadingSessionEntryRepository readingSessionEntryRepository) {
        this.readingSessionEntryRepository = readingSessionEntryRepository;
    }

    public void execute(CreateReadingSessionEntryCommand command){
    }

}
