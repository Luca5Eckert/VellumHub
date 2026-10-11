package com.vellumhub.engagement_service.module.reading_progress.infrastructure.kafka;
import com.vellumhub.engagement_service.module.reading_progress.application.use_case.ProcessOrderedReadingProgressUseCase;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
public class OrderedReadingProgressConsumer {
    private final ProcessOrderedReadingProgressUseCase process;
    public OrderedReadingProgressConsumer(ProcessOrderedReadingProgressUseCase process) { this.process=process; }
    @KafkaListener(topics=KafkaTopics.READING_PROGRESS, groupId=KafkaConsumerGroups.ENGAGEMENT_SERVICE,
                   containerFactory="orderedReadingProgressContainerFactory")
    public void consume(ReadingProgressChangedEvent event) { process.execute(event); }
}

