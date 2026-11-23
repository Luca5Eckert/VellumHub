package com.vellumhub.recommendation_service.module.user_profile.infrastructure.consumer;

import com.vellumhub.kafka.contracts.user.UserPreferenceChanged;
import com.vellumhub.recommendation_service.module.user_profile.application.command.CreatedUserProfileCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.CreateUserProfileUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testcontainers
class OrderedPreferenceProjectionPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @Test
    void rejectsGapsAndIdentityCollisionsAndRollsBackTheCheckpointWithProjectionFailure() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V7__ordered_preference_projection.sql"))
                .execute(dataSource);
        var jdbc = JdbcClient.create(dataSource);
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var profiles = mock(CreateUserProfileUseCase.class);
        var projection = new OrderedPreferenceProjection(jdbc, profiles);
        UUID subject = UUID.randomUUID();
        var first = event(subject, 0);
        var second = event(subject, 1);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> projection.apply(second)))
                .hasMessage("Preference revision gap");
        transactions.executeWithoutResult(status -> projection.apply(first));
        transactions.executeWithoutResult(status -> projection.apply(first));
        verify(profiles, times(1)).execute(command(first));

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> projection.apply(event(subject, 0))))
                .hasMessage("Preference revision has a different event identity");
        var reused = new UserPreferenceChanged(first.eventId(), first.occurredAt(), subject, 1, first.genres(), null);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> projection.apply(reused)))
                .isInstanceOf(DataIntegrityViolationException.class);

        doThrow(new IllegalStateException("embedding failed")).doNothing().when(profiles).execute(command(second));
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> projection.apply(second)))
                .hasMessage("embedding failed");
        assertThat(jdbc.sql("SELECT last_revision FROM preference_event_offsets").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT revision FROM processed_preference_events").query(Long.class).list())
                .containsExactly(0L);

        transactions.executeWithoutResult(status -> projection.apply(second));
        assertThat(jdbc.sql("SELECT last_revision FROM preference_event_offsets").query(Long.class).single()).isEqualTo(1);
    }

    private static UserPreferenceChanged event(UUID user, long revision) {
        return new UserPreferenceChanged(UUID.randomUUID(), Instant.EPOCH, user, revision, List.of("Fantasy"), null);
    }

    private static CreatedUserProfileCommand command(UserPreferenceChanged event) {
        return CreatedUserProfileCommand.of(event.userId(), event.genres(), event.about());
    }
}
