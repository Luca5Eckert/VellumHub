package com.mrs.user_service.module.auth.domain.handler;

import com.mrs.user_service.module.user.domain.UserEntity;
import com.mrs.user_service.module.user.domain.port.UserRepository;
import org.passay.PasswordData;
import org.passay.PasswordValidator;
import org.passay.RuleResult;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterUserHandler {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;

    public RegisterUserHandler(UserRepository userRepository, PasswordEncoder passwordEncoder, PasswordValidator passwordValidator) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordValidator = passwordValidator;
    }

    @Transactional
    public void execute(UserEntity userEntity) {
        if (userEntity == null) throw new IllegalArgumentException("User can't be null");

        if (userRepository.existsByEmail(userEntity.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        String rawPassword = userEntity.getPassword();

        validatePassword(rawPassword);

        userEntity.setPassword(passwordEncoder.encode(rawPassword));

        userRepository.save(userEntity);
    }

    private void validatePassword(String password) {
        RuleResult result = passwordValidator.validate(new PasswordData(password));

        if (!result.isValid()) {
            String messages = passwordValidator.getMessages(result).stream()
                    .reduce((m1, m2) -> m1 + ", " + m2)
                    .orElse("Invalid password");

            throw new IllegalArgumentException("Password invalid: " + messages);
        }
    }

}