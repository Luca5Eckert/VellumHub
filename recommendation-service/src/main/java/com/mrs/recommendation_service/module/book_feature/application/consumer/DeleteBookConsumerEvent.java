package com.mrs.recommendation_service.module.book_feature.application.consumer;

import com.mrs.recommendation_service.module.book_feature.application.event.DeleteBookEvent;
import com.mrs.recommendation_service.module.book_feature.domain.use_case.DeleteBookFeatureUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DeleteBookConsumerEvent {

    private final DeleteBookFeatureUseCase deleteBookFeatureUseCase;

    public DeleteBookConsumerEvent(DeleteBookFeatureUseCase deleteBookFeatureUseCase) {
        this.deleteBookFeatureUseCase = deleteBookFeatureUseCase;
    }


    @KafkaListener(
            topics = "deleted-book",
            groupId = "recommendation-service"
    )
    public void listen(DeleteBookEvent deleteBookEvent){
        log.info("Evento recebido: Exclusão de livro. BookId={}",
                deleteBookEvent.bookId());

        try {
            deleteBookFeatureUseCase.execute(deleteBookEvent.bookId());

            log.info("Evento de exclusão de livro processado com sucesso. BookId={}",
                    deleteBookEvent.bookId());

        } catch (Exception e) {
            log.error("Erro ao processar evento de exclusão de livro. BookId={}, Erro: {}",
                    deleteBookEvent.bookId(),
                    e.getMessage(),
                    e);
            throw e;
        }
    }

}
