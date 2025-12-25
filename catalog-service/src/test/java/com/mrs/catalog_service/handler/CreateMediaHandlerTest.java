package com.mrs.catalog_service.handler;

import com.mrs.catalog_service.event.CreateMediaEvent;
import com.mrs.catalog_service.model.Genre;
import com.mrs.catalog_service.model.Media;
import com.mrs.catalog_service.model.MediaType;
import com.mrs.catalog_service.repository.MediaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateMediaHandler Tests")
class CreateMediaHandlerTest {

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private KafkaTemplate<String, CreateMediaEvent> kafka;

    @InjectMocks
    private CreateMediaHandler createMediaHandler;

    @Test
    @DisplayName("Should save media and publish Kafka event")
    void handler_WithValidMedia_SavesAndPublishesEvent() {
        Media media = new Media.Builder()
                .title("Inception")
                .description("A mind-bending thriller")
                .releaseYear(2010)
                .mediaType(MediaType.MOVIE)
                .genres(List.of(Genre.ACTION, Genre.THRILLER))
                .createAt(Instant.now())
                .updateAt(Instant.now())
                .build();
        
        // Set ID via reflection or mock return
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> {
            Media savedMedia = invocation.getArgument(0);
            // Simulate ID assignment
            return savedMedia;
        });

        createMediaHandler.handler(media);

        verify(mediaRepository, times(1)).save(media);
        verify(kafka, times(1)).send(eq("create-media"), anyString(), any(CreateMediaEvent.class));
    }

    @Test
    @DisplayName("Should throw exception when media is null")
    void handler_WithNullMedia_ThrowsException() {
        assertThatThrownBy(() -> createMediaHandler.handler(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Media can't be null");

        verifyNoInteractions(mediaRepository);
        verifyNoInteractions(kafka);
    }

    @Test
    @DisplayName("Should publish event with correct genre list")
    void handler_WithMedia_PublishesEventWithGenres() {
        Media media = new Media.Builder()
                .title("Horror Movie")
                .genres(List.of(Genre.HORROR, Genre.THRILLER))
                .createAt(Instant.now())
                .build();

        when(mediaRepository.save(any(Media.class))).thenReturn(media);

        createMediaHandler.handler(media);

        ArgumentCaptor<CreateMediaEvent> eventCaptor = ArgumentCaptor.forClass(CreateMediaEvent.class);
        verify(kafka).send(eq("create-media"), anyString(), eventCaptor.capture());

        CreateMediaEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.genres()).containsExactly("HORROR", "THRILLER");
    }

    @Test
    @DisplayName("Should use media ID as Kafka key")
    void handler_WithMedia_UsesMediaIdAsKafkaKey() {
        UUID mediaId = UUID.randomUUID();
        Media media = new Media.Builder()
                .title("Test Movie")
                .genres(List.of(Genre.ACTION))
                .createAt(Instant.now())
                .build();
        media.setId(mediaId);

        when(mediaRepository.save(any(Media.class))).thenReturn(media);

        createMediaHandler.handler(media);

        verify(kafka).send(eq("create-media"), eq(mediaId.toString()), any(CreateMediaEvent.class));
    }
}
