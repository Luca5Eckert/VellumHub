package com.mrs.recommendation_service.module.book_feature.application.consumer;

import com.mrs.recommendation_service.module.book_feature.application.event.UpdateBookEvent;
import com.mrs.recommendation_service.module.book_feature.domain.command.UpdateBookFeatureCommand;
import com.mrs.recommendation_service.module.book_feature.domain.use_case.UpdateMediaFeatureUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UpdateBookConsumerEvent {

    private final UpdateMediaFeatureUseCase mediaFeatureHandler;

    public UpdateBookConsumerEvent(UpdateMediaFeatureUseCase mediaFeatureHandler) {
        this.mediaFeatureHandler = mediaFeatureHandler;
    }

    @KafkaListener(
            topics = "updated-book",
            groupId = "recommendation-service"
    )
    public void execute(UpdateBookEvent updateBookEvent){
        log.info("Evento recebido: Atualização de livro. BookId={}, Genres={}",
                updateBookEvent.bookId(),
                updateBookEvent.genres());

        try {
            log.debug("Iniciando processamento do evento de atualização de livro. BookId={}, Genres={}",
                    updateBookEvent.bookId(),
                    updateBookEvent.genres());

            UpdateBookFeatureCommand mediaFeatureCommand = new UpdateBookFeatureCommand(
                    updateBookEvent.bookId(),
                    updateBookEvent.genres()
            );

            mediaFeatureHandler.execute(mediaFeatureCommand);

            log.info("Evento de atualização de livro processado com sucesso. BookId={}",
                    updateBookEvent.bookId());

        } catch (Exception e) {
            log.error("Erro ao processar evento de atualização de livro. BookId={}, Genres={}, Erro: {}",
                    updateBookEvent.bookId(),
                    updateBookEvent.genres(),
                    e.getMessage(),
                    e);
            throw e;
        }
    }

}
