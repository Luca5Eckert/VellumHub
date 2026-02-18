package com.mrs.recommendation_service.module.book_feature.application.consumer;

import com.mrs.recommendation_service.module.book_feature.application.event.CreateBookEvent;
import com.mrs.recommendation_service.module.book_feature.application.mapper.BookFeatureMapper;
import com.mrs.recommendation_service.module.book_feature.domain.use_case.CreateBookFeatureUseCase;
import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CreateBookConsumerEvent {

    private final CreateBookFeatureUseCase createBookFeatureUseCase;
    private final BookFeatureMapper mapper;

    public CreateBookConsumerEvent(CreateBookFeatureUseCase createBookFeatureUseCase, BookFeatureMapper mapper) {
        this.createBookFeatureUseCase = createBookFeatureUseCase;
        this.mapper = mapper;
    }


    @KafkaListener(
            topics = "created-book",
            groupId = "recommendation-service"
    )
    public void listen(CreateBookEvent createBookEvent){
        log.info("Evento recebido: Criação de livro. BookId={}, Genres={}",
                createBookEvent.bookId(),
                createBookEvent.genres());

        try {
            log.debug("Iniciando processamento do evento de criação de livro. BookId={}, Genres={}",
                    createBookEvent.bookId(),
                    createBookEvent.genres());

            float[] genresVector = mapper.mapToFeatureVector(createBookEvent.genres());

            log.debug("Vetor de características criado. BookId={}, VectorLength={}",
                    createBookEvent.bookId(),
                    genresVector.length);

            BookFeature bookFeature = new BookFeature(
                    createBookEvent.bookId(),
                    genresVector
            );

            createBookFeatureUseCase.execute(bookFeature);

            log.info("Evento de criação de livro processado com sucesso. BookId={}",
                    createBookEvent.bookId());

        } catch (Exception e) {
            log.error("Erro ao processar evento de criação de livro. BookId={}, Genres={}, Erro: {}",
                    createBookEvent.bookId(),
                    createBookEvent.genres(),
                    e.getMessage(),
                    e);
            throw e;
        }
    }

}
