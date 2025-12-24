package com.mrs.user_service.handler.user;

import com.mrs.user_service.dto.PageUser;
import com.mrs.user_service.model.UserEntity;
import com.mrs.user_service.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetAllUserHandlerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GetAllUserHandler getAllUserHandler;

    @Test
    @DisplayName("Deve chamar o repositório com os parâmetros de paginação corretos")
    void execute_ShouldCallRepositoryWithCorrectPagination() {
        // Arrange
        int page = 2;
        int size = 10;
        PageUser pageUser = new PageUser(page, size);

        Page<UserEntity> expectedPage = new PageImpl<>(List.of(new UserEntity()));
        when(userRepository.findAll(any(PageRequest.class))).thenReturn(expectedPage);

        // Act
        Page<UserEntity> result = getAllUserHandler.execute(pageUser);

        // Assert
        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(userRepository).findAll(captor.capture());

        PageRequest capturedRequest = captor.getValue();
        assertThat(capturedRequest.getPageNumber()).isEqualTo(page);
        assertThat(capturedRequest.getPageSize()).isEqualTo(size);
        assertThat(result).isEqualTo(expectedPage);
    }
}