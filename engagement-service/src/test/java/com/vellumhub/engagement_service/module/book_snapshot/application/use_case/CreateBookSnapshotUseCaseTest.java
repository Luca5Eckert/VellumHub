package com.vellumhub.engagement_service.module.book_snapshot.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.application.command.CreateBookSnapshotCommand;
import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateBookSnapshotUseCaseTest {

    @Mock
    private BookSnapshotRepository bookSnapshotRepository;

    @InjectMocks
    private CreateBookSnapshotUseCase createBookSnapshotUseCase;

    @Test
    @DisplayName("Should create snapshot with page count when snapshot does not exist")
    void shouldCreateSnapshotWhenItDoesNotExist() {
        UUID bookId = UUID.randomUUID();

        when(bookSnapshotRepository.findById(bookId)).thenReturn(Optional.empty());

        createBookSnapshotUseCase.execute(new CreateBookSnapshotCommand(bookId, 320));

        ArgumentCaptor<BookSnapshot> captor = ArgumentCaptor.forClass(BookSnapshot.class);
        verify(bookSnapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getBookId()).isEqualTo(bookId);
        assertThat(captor.getValue().getPageCount()).isEqualTo(320);
    }

    @Test
    @DisplayName("Should update page count when snapshot already exists")
    void shouldUpdatePageCountWhenSnapshotAlreadyExists() {
        UUID bookId = UUID.randomUUID();
        BookSnapshot existing = new BookSnapshot();
        existing.setBookId(bookId);
        existing.setPageCount(100);

        when(bookSnapshotRepository.findById(bookId)).thenReturn(Optional.of(existing));

        createBookSnapshotUseCase.execute(new CreateBookSnapshotCommand(bookId, 250));

        verify(bookSnapshotRepository).save(existing);
        assertThat(existing.getPageCount()).isEqualTo(250);
    }
}
