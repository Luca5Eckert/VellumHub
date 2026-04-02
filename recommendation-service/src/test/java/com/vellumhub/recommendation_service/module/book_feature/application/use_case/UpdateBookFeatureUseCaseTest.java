package com.vellumhub.recommendation_service.module.book_feature.application.use_case;

import com.vellumhub.recommendation_service.module.book_feature.application.command.UpdateBookFeatureCommand;
import com.vellumhub.recommendation_service.module.book_feature.domain.exception.BookFeatureNotFoundException;
import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.EmbeddingBookProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateBookFeatureUseCase")
class UpdateBookFeatureUseCaseTest {

    @Mock
    private BookFeatureRepository bookFeatureRepository;

    @Mock
    private EmbeddingBookProvider embeddingBookProvider;

    @Mock
    private BookFeature bookFeature;

    @InjectMocks
    private UpdateBookFeatureUseCase useCase;

    private UUID bookId;
    private String title;
    private String author;
    private String description;
    private List<String> genres;
    private float[] vectors;

    @BeforeEach
    void setUp() {
        bookId = UUID.randomUUID();
        title = "Dune";
        author = "Frank Herbert";
        description = "A sci-fi epic set in a desert planet.";
        genres = List.of("Science Fiction", "Adventure");
        vectors = new float[384];
        for (int i = 0; i < 384; i++) {
            vectors[i] = (float) Math.random();
        }
    }

    @Nested
    @DisplayName("execute()")
    class Execute {

        @Test
        @DisplayName("should look up the book by the ID provided in the command")
        void shouldFindBookByIdFromCommand() {
            var command = UpdateBookFeatureCommand.of(bookId, title, author, description, genres);
            when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
            when(embeddingBookProvider.of(title, author, description, genres)).thenReturn(vectors);

            useCase.execute(command);

            verify(bookFeatureRepository).findById(bookId);
        }

        @Test
        @DisplayName("should throw BookFeatureNotFoundException when book does not exist")
        void shouldThrowWhenBookNotFound() {
            var command = UpdateBookFeatureCommand.of(bookId, title, author, description, genres);
            when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.execute(command))
                    .isInstanceOf(BookFeatureNotFoundException.class);
        }

        @Test
        @DisplayName("should not call the embedding provider when the book is not found")
        void shouldNotCallProviderWhenBookNotFound() {
            var command = UpdateBookFeatureCommand.of(bookId, title, author, description, genres);
            when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.empty());

            try {
                useCase.execute(command);
            } catch (BookFeatureNotFoundException ignored) {}

            verifyNoInteractions(embeddingBookProvider);
        }

        @Test
        @DisplayName("should not save when the book is not found")
        void shouldNotSaveWhenBookNotFound() {
            var command = UpdateBookFeatureCommand.of(bookId, title, author, description, genres);
            when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.empty());

            try {
                useCase.execute(command);
            } catch (BookFeatureNotFoundException ignored) {}

            verify(bookFeatureRepository, never()).save(any());
        }

        @Test
        @DisplayName("should generate embeddings with the exact fields from the command")
        void shouldDelegateEmbeddingGenerationWithCorrectArguments() {
            var command = UpdateBookFeatureCommand.of(bookId, title, author, description, genres);
            when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
            when(embeddingBookProvider.of(title, author, description, genres)).thenReturn(vectors);

            useCase.execute(command);

            verify(embeddingBookProvider).of(title, author, description, genres);
        }

        @Test
        @DisplayName("should apply the generated embedding to the retrieved BookFeature")
        void shouldApplyEmbeddingToBookFeature() {
            var command = UpdateBookFeatureCommand.of(bookId, title, author, description, genres);
            when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
            when(embeddingBookProvider.of(title, author, description, genres)).thenReturn(vectors);

            useCase.execute(command);

            verify(bookFeature).updateEmbedding(vectors);
        }

        @Test
        @DisplayName("should persist the updated BookFeature")
        void shouldSaveUpdatedBookFeature() {
            var command = UpdateBookFeatureCommand.of(bookId, title, author, description, genres);
            when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
            when(embeddingBookProvider.of(title, author, description, genres)).thenReturn(vectors);

            useCase.execute(command);

            verify(bookFeatureRepository).save(bookFeature);
        }

        @Test
        @DisplayName("should persist the exact same instance retrieved from the repository")
        void shouldSaveTheSameInstanceReturnedByRepository() {
            var command = UpdateBookFeatureCommand.of(bookId, title, author, description, genres);
            when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
            when(embeddingBookProvider.of(title, author, description, genres)).thenReturn(vectors);
            var captor = ArgumentCaptor.forClass(BookFeature.class);

            useCase.execute(command);

            verify(bookFeatureRepository).save(captor.capture());
            assertThat(captor.getValue()).isSameAs(bookFeature);
        }

        @Test
        @DisplayName("should save exactly once per execution")
        void shouldSaveExactlyOnce() {
            var command = UpdateBookFeatureCommand.of(bookId, title, author, description, genres);
            when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
            when(embeddingBookProvider.of(title, author, description, genres)).thenReturn(vectors);

            useCase.execute(command);

            verify(bookFeatureRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("should enforce order: find → embed → updateEmbedding → save")
        void shouldEnforceExecutionOrder() {
            var command = UpdateBookFeatureCommand.of(bookId, title, author, description, genres);
            when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
            when(embeddingBookProvider.of(title, author, description, genres)).thenReturn(vectors);
            var order = inOrder(bookFeatureRepository, embeddingBookProvider, bookFeature);

            useCase.execute(command);

            order.verify(bookFeatureRepository).findById(bookId);
            order.verify(embeddingBookProvider).of(title, author, description, genres);
            order.verify(bookFeature).updateEmbedding(vectors);
            order.verify(bookFeatureRepository).save(bookFeature);
        }

        @Test
        @DisplayName("should propagate runtime exception thrown by the embedding provider")
        void shouldPropagateEmbeddingProviderException() {
            var command = UpdateBookFeatureCommand.of(bookId, title, author, description, genres);
            when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
            when(embeddingBookProvider.of(anyString(), anyString(), anyString(), anyList()))
                    .thenThrow(new RuntimeException("Embedding service unavailable"));

            assertThatThrownBy(() -> useCase.execute(command))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Embedding service unavailable");

            verify(bookFeatureRepository, never()).save(any());
        }

        @Test
        @DisplayName("should propagate runtime exception thrown by the repository on save")
        void shouldPropagateRepositorySaveException() {
            var command = UpdateBookFeatureCommand.of(bookId, title, author, description, genres);
            when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
            when(embeddingBookProvider.of(title, author, description, genres)).thenReturn(vectors);
            doThrow(new RuntimeException("Database unavailable"))
                    .when(bookFeatureRepository).save(any());

            assertThatThrownBy(() -> useCase.execute(command))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Database unavailable");
        }

        @Test
        @DisplayName("should handle an empty genres list without error")
        void shouldHandleEmptyGenresList() {
            var command = UpdateBookFeatureCommand.of(bookId, title, author, description, Collections.emptyList());
            when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
            when(embeddingBookProvider.of(title, author, description, Collections.emptyList())).thenReturn(vectors);

            useCase.execute(command);

            verify(bookFeatureRepository).save(bookFeature);
        }

        @Test
        @DisplayName("should never call the embedding provider more than once per execution")
        void shouldNeverCallProviderMoreThanOnce() {
            var command = UpdateBookFeatureCommand.of(bookId, title, author, description, genres);
            when(bookFeatureRepository.findById(bookId)).thenReturn(Optional.of(bookFeature));
            when(embeddingBookProvider.of(title, author, description, genres)).thenReturn(vectors);

            useCase.execute(command);

            verify(embeddingBookProvider, times(1)).of(anyString(), anyString(), anyString(), anyList());
        }
    }

    @Nested
    @DisplayName("UpdateBookFeatureCommand.of()")
    class CommandFactory {

        @Test
        @DisplayName("should construct command with all provided values")
        void shouldConstructCommandWithAllValues() {
            var command = UpdateBookFeatureCommand.of(bookId, title, author, description, genres);

            assertThat(command.bookId()).isEqualTo(bookId);
            assertThat(command.title()).isEqualTo(title);
            assertThat(command.author()).isEqualTo(author);
            assertThat(command.description()).isEqualTo(description);
            assertThat(command.genres()).isEqualTo(genres);
        }

        @Test
        @DisplayName("should return a different instance on each invocation with equal value")
        void shouldReturnNewInstanceWithEqualValueOnEachCall() {
            var first = UpdateBookFeatureCommand.of(bookId, title, author, description, genres);
            var second = UpdateBookFeatureCommand.of(bookId, title, author, description, genres);

            assertThat(first).isEqualTo(second);
            assertThat(first).isNotSameAs(second);
        }
    }
}