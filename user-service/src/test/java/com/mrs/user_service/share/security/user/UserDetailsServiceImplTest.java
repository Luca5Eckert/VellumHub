package com.mrs.user_service.share.security.user;

import com.mrs.user_service.module.user.domain.RoleUser;
import com.mrs.user_service.module.user.domain.UserEntity;
import com.mrs.user_service.module.user.domain.port.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @Test
    @DisplayName("Should load user details when user exists")
    void shouldLoadUserDetailsSuccessfully() {
        UUID userId = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("user@test.com");
        user.setPassword("encoded-password");
        user.setRole(RoleUser.USER);

        when(userRepository.findByEmail("user@test.com"))
                .thenReturn(Optional.of(user));

        UserDetails userDetails =
                userDetailsService.loadUserByUsername("user@test.com");

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo("user@test.com");
        assertThat(userDetails.getPassword()).isEqualTo("encoded-password");
        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactly(RoleUser.USER.name());

        verify(userRepository).findByEmail("user@test.com");
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("Should throw UsernameNotFoundException when user does not exist")
    void shouldThrowExceptionWhenUserNotFound() {
        when(userRepository.findByEmail("missing@test.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userDetailsService.loadUserByUsername("missing@test.com")
        )
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Invalid credentials");

        verify(userRepository).findByEmail("missing@test.com");
        verifyNoMoreInteractions(userRepository);
    }
}
