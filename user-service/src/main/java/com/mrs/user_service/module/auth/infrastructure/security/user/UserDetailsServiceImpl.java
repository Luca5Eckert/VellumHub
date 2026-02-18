package com.mrs.user_service.module.auth.infrastructure.security.user;

import com.mrs.user_service.module.user.domain.UserEntity;
import com.mrs.user_service.module.user.domain.port.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow( () -> new UsernameNotFoundException("Invalid credentials"));

        return new UserDetailImpl(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getRole()
        );
    }

}
