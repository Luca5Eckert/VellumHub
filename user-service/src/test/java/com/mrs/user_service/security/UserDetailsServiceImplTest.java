package com.mrs.user_service.security;

import com.mrs.user_service.model.RoleUser;
import com.mrs.user_service.model.UserEntity;
import com.mrs.user_service.repository.UserRepository;
import com.mrs.user_service.security.user.UserDetailImpl;
import com.mrs.user_service.security.user.UserDetailsServiceImpl;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserDetailsServiceImpl Tests")
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @Test
    @DisplayName("Should return UserDetails when user exists")
    void loadUserByUsername_WhenUserExists_ReturnsUserDetails() {
        UUID userId = UUID.randomUUID();
        String email = "john@example.com";
        String password = "encodedPassword";
        
        UserEntity userEntity = UserEntity.builder()
                .id(userId)
                .email(email)
                .password(password)
                .role(RoleUser.USER)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(userEntity));

        UserDetails result = userDetailsService.loadUserByUsername(email);

        assertThat(result).isInstanceOf(UserDetailImpl.class);
        assertThat(result.getUsername()).isEqualTo(email);
        assertThat(result.getPassword()).isEqualTo(password);
        assertThat(result.getAuthorities()).hasSize(1);
        assertThat(result.getAuthorities().iterator().next().getAuthority()).isEqualTo("USER");
    }

    @Test
    @DisplayName("Should return ADMIN authorities when user has ADMIN role")
    void loadUserByUsername_WithAdminRole_ReturnsAdminAuthorities() {
        UUID userId = UUID.randomUUID();
        String email = "admin@example.com";
        
        UserEntity userEntity = UserEntity.builder()
                .id(userId)
                .email(email)
                .password("password")
                .role(RoleUser.ADMIN)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(userEntity));

        UserDetails result = userDetailsService.loadUserByUsername(email);

        assertThat(result.getAuthorities().iterator().next().getAuthority()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Should throw UsernameNotFoundException when user not found")
    void loadUserByUsername_WhenUserNotFound_ThrowsException() {
        String email = "nonexistent@example.com";

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(email))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    @DisplayName("Should include userId in UserDetailImpl")
    void loadUserByUsername_ReturnsUserDetailWithUserId() {
        UUID userId = UUID.randomUUID();
        String email = "john@example.com";
        
        UserEntity userEntity = UserEntity.builder()
                .id(userId)
                .email(email)
                .password("password")
                .role(RoleUser.USER)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(userEntity));

        UserDetails result = userDetailsService.loadUserByUsername(email);

        assertThat(result).isInstanceOf(UserDetailImpl.class);
        assertThat(((UserDetailImpl) result).getUserId()).isEqualTo(userId);
    }
}
