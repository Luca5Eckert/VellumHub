package com.vellumhub.engagement_service.module.reaction.application.use_case;

import com.vellumhub.engagement_service.module.reaction.application.query.GetReactionQuery;
import com.vellumhub.engagement_service.module.reaction.application.query.GetAllReactionByUserQuery;
import com.vellumhub.engagement_service.module.reaction.application.command.UpdateReactionCommand;
import com.vellumhub.engagement_service.module.reaction.domain.exception.ReactionNotFoundException;
import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.domain.model.TypeReaction;
import com.vellumhub.engagement_service.module.reaction.domain.port.ReactionRepository;
import com.vellumhub.engagement_service.module.reaction.application.event.ReactionEventRecorder;
import com.vellumhub.engagement_service.share.metrics.VellumHubMetrics;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ReactionOwnershipUseCaseTest {
    private final ReactionRepository repository = mock(ReactionRepository.class);
    private final GetReactionUseCase get = new GetReactionUseCase(repository);
    private final GetAllReactionByUserUseCase list = new GetAllReactionByUserUseCase(repository);

    @Test
    void readingOtherUsersReactionIsHiddenEvenWhenIdExists() {
        UUID owner = UUID.randomUUID();
        Reaction reaction = Reaction.builder().id(1L).userId(owner).typeReaction(TypeReaction.POSITIVE).build();
        when(repository.findById(1L)).thenReturn(Optional.of(reaction));
        assertThatThrownBy(() -> get.execute(GetReactionQuery.of(1L, UUID.randomUUID())))
                .isInstanceOf(ReactionNotFoundException.class);
        assertThat(get.execute(GetReactionQuery.of(1L, owner))).isSameAs(reaction);
        assertThatThrownBy(() -> get.execute(GetReactionQuery.of(2L, owner)))
                .isInstanceOf(ReactionNotFoundException.class);
    }

    @Test
    void listingAnotherUsersReactionsDoesNotQueryDatabase() {
        UUID owner = UUID.randomUUID();
        assertThatThrownBy(() -> list.execute(GetAllReactionByUserQuery.of(owner, UUID.randomUUID())))
                .isInstanceOf(ReactionNotFoundException.class);
        verifyNoInteractions(repository);
        when(repository.findAllByUserId(owner)).thenReturn(List.of());
        assertThat(list.execute(GetAllReactionByUserQuery.of(owner, owner))).isEmpty();
    }

    @Test
    void updateByStrangerDoesNotSaveOrPublishEvent() {
        UUID owner = UUID.randomUUID();
        Reaction reaction = Reaction.builder().id(42L).userId(owner).typeReaction(TypeReaction.POSITIVE).build();
        when(repository.findByIdForUpdate(42L)).thenReturn(Optional.of(reaction));
        var events = mock(ReactionEventRecorder.class);
        var metrics = mock(VellumHubMetrics.class);
        var useCase = new UpdateReactionUseCase(repository, events, metrics);
        assertThatThrownBy(() -> useCase.execute(UpdateReactionCommand.of(UUID.randomUUID(), 42L, TypeReaction.POSITIVE)))
                .isInstanceOf(ReactionNotFoundException.class);
        verify(repository, never()).save(any());
        verifyNoInteractions(events);
        assertThat(reaction.getTypeReaction()).isEqualTo(TypeReaction.POSITIVE);
    }
}
