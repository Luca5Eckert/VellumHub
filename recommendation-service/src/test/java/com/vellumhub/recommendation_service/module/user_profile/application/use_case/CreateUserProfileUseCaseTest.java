package com.vellumhub.recommendation_service.module.user_profile.application.use_case;

import com.vellumhub.recommendation_service.module.user_profile.application.command.CreatedUserProfileCommand;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.UserProfile;
import com.vellumhub.recommendation_service.module.user_profile.domain.port.EmbeddingUserProfileProvider;
import com.vellumhub.recommendation_service.module.user_profile.domain.port.UserProfileRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateUserProfileUseCase")
class CreateUserProfileUseCaseTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private EmbeddingUserProfileProvider profileProvider;

    @InjectMocks
    private CreateUserProfileUseCase useCase;

    private UUID userId;
    private List<String> genres;
    private String about;
    private float[] vectors;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        genres = List.of("Fantasy", "Science Fiction");
        about = "I enjoy epic narratives and world-building stories.";
        vectors = new float[384];
        for (int i = 0; i < 384; i++) {
            vectors[i] = (float) Math.random();
        }
    }

    @Nested
    @DisplayName("execute()")
    class Execute {

        @Test
        @DisplayName("should generate embeddings using genres and about from command")
        void shouldDelegateEmbeddingGenerationWithCorrectArguments() {
            var command = CreatedUserProfileCommand.of(userId, genres, about);
            when(profileProvider.of(genres, about)).thenReturn(vectors);

            useCase.execute(command);

            verify(profileProvider).of(genres, about);
        }

        @Test
        @DisplayName("should persist a UserProfile with the userId from the command")
        void shouldSaveUserProfileWithCorrectUserId() {
            var command = CreatedUserProfileCommand.of(userId, genres, about);
            when(profileProvider.of(genres, about)).thenReturn(vectors);
            var captor = ArgumentCaptor.forClass(UserProfile.class);

            useCase.execute(command);

            verify(userProfileRepository).save(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("should persist a UserProfile with the embedding returned by the provider")
        void shouldSaveUserProfileWithCorrectEmbeddingVector() {
            var command = CreatedUserProfileCommand.of(userId, genres, about);
            when(profileProvider.of(genres, about)).thenReturn(vectors);
            var captor = ArgumentCaptor.forClass(UserProfile.class);

            useCase.execute(command);

            verify(userProfileRepository).save(captor.capture());
            assertThat(captor.getValue().getProfileVector()).isEqualTo(vectors);
        }

        @Test
        @DisplayName("should save the UserProfile exactly once")
        void shouldSaveExactlyOnce() {
            var command = CreatedUserProfileCommand.of(userId, genres, about);
            when(profileProvider.of(genres, about)).thenReturn(vectors);

            useCase.execute(command);

            verify(userProfileRepository, times(1)).save(any(UserProfile.class));
        }

        @Test
        @DisplayName("should initialise the profile with zero engagement score")
        void shouldInitialiseProfileWithZeroEngagementScore() {
            var command = CreatedUserProfileCommand.of(userId, genres, about);
            when(profileProvider.of(genres, about)).thenReturn(vectors);
            var captor = ArgumentCaptor.forClass(UserProfile.class);

            useCase.execute(command);

            verify(userProfileRepository).save(captor.capture());
            assertThat(captor.getValue().getTotalEngagementScore()).isZero();
        }

        @Test
        @DisplayName("should initialise the profile with an empty set of interacted book IDs")
        void shouldInitialiseProfileWithEmptyInteractedBookIds() {
            var command = CreatedUserProfileCommand.of(userId, genres, about);
            when(profileProvider.of(genres, about)).thenReturn(vectors);
            var captor = ArgumentCaptor.forClass(UserProfile.class);

            useCase.execute(command);

            verify(userProfileRepository).save(captor.capture());
            assertThat(captor.getValue().getInteractedBookIds()).isEmpty();
        }

        @Test
        @DisplayName("should initialise createdAt and lastUpdated timestamps")
        void shouldInitialiseTimestamps() {
            var command = CreatedUserProfileCommand.of(userId, genres, about);
            when(profileProvider.of(genres, about)).thenReturn(vectors);
            var captor = ArgumentCaptor.forClass(UserProfile.class);

            useCase.execute(command);

            verify(userProfileRepository).save(captor.capture());
            var saved = captor.getValue();
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getLastUpdated()).isNotNull();
        }

        @Test
        @DisplayName("should propagate runtime exception thrown by the embedding provider")
        void shouldPropagateEmbeddingProviderException() {
            var command = CreatedUserProfileCommand.of(userId, genres, about);
            when(profileProvider.of(anyList(), anyString()))
                    .thenThrow(new RuntimeException("Embedding service unavailable"));

            assertThatThrownBy(() -> useCase.execute(command))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Embedding service unavailable");

            verifyNoInteractions(userProfileRepository);
        }

        @Test
        @DisplayName("should propagate runtime exception thrown by the repository")
        void shouldPropagateRepositoryException() {
            var command = CreatedUserProfileCommand.of(userId, genres, about);
            when(profileProvider.of(genres, about)).thenReturn(vectors);
            doThrow(new RuntimeException("Database unavailable"))
                    .when(userProfileRepository).save(any(UserProfile.class));

            assertThatThrownBy(() -> useCase.execute(command))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Database unavailable");
        }

        @Test
        @DisplayName("should not save when the embedding provider fails")
        void shouldNotSaveWhenProviderFails() {
            var command = CreatedUserProfileCommand.of(userId, genres, about);
            when(profileProvider.of(anyList(), anyString()))
                    .thenThrow(new RuntimeException("Provider error"));

            try {
                useCase.execute(command);
            } catch (RuntimeException ignored) {}

            verifyNoInteractions(userProfileRepository);
        }

        @Test
        @DisplayName("should handle an empty genres list without error")
        void shouldHandleEmptyGenresList() {
            var command = CreatedUserProfileCommand.of(userId, Collections.emptyList(), about);
            when(profileProvider.of(Collections.emptyList(), about)).thenReturn(vectors);

            useCase.execute(command);

            verify(userProfileRepository).save(any(UserProfile.class));
        }

        @Test
        @DisplayName("should handle a blank about string without error")
        void shouldHandleBlankAboutString() {
            var command = CreatedUserProfileCommand.of(userId, genres, "");
            when(profileProvider.of(genres, "")).thenReturn(vectors);

            useCase.execute(command);

            verify(userProfileRepository).save(any(UserProfile.class));
        }

        @Test
        @DisplayName("should forward genres and about fields exactly as supplied in the command")
        void shouldForwardCommandFieldsExactlyToProvider() {
            var specificGenres = List.of("Horror", "Thriller", "Mystery");
            var specificAbout = "I prefer dark psychological plots.";
            var command = CreatedUserProfileCommand.of(userId, specificGenres, specificAbout);
            when(profileProvider.of(specificGenres, specificAbout)).thenReturn(vectors);

            useCase.execute(command);

            verify(profileProvider).of(specificGenres, specificAbout);
            verifyNoMoreInteractions(profileProvider);
        }

        @Test
        @DisplayName("should never invoke the provider more than once per execution")
        void shouldNeverInvokeProviderMoreThanOnce() {
            var command = CreatedUserProfileCommand.of(userId, genres, about);
            when(profileProvider.of(genres, about)).thenReturn(vectors);

            useCase.execute(command);

            verify(profileProvider, times(1)).of(anyList(), anyString());
        }
    }

    @Nested
    @DisplayName("CreatedUserProfileCommand.of()")
    class CommandFactory {

        @Test
        @DisplayName("should construct command with all provided values")
        void shouldConstructCommandWithAllValues() {
            var command = CreatedUserProfileCommand.of(userId, genres, about);

            assertThat(command.userId()).isEqualTo(userId);
            assertThat(command.genres()).isEqualTo(genres);
            assertThat(command.about()).isEqualTo(about);
        }

        @Test
        @DisplayName("should return a different instance on each invocation")
        void shouldReturnNewInstanceOnEachCall() {
            var first = CreatedUserProfileCommand.of(userId, genres, about);
            var second = CreatedUserProfileCommand.of(userId, genres, about);

            assertThat(first).isEqualTo(second);
            assertThat(first).isNotSameAs(second);
        }
    }
}