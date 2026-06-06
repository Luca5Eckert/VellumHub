package com.vellumhub.user_service.module.auth.domain.handler;

import com.vellumhub.user_service.module.auth.domain.exception.AuthDomainException;
import com.vellumhub.user_service.module.user.domain.UserEntity;
import com.vellumhub.user_service.module.user.domain.port.UserRepository;
import com.vellumhub.user_service.share.metrics.VellumHubMetrics;
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
    private final VellumHubMetrics metrics;

    public RegisterUserHandler(UserRepository userRepository, PasswordEncoder passwordEncoder, PasswordValidator passwordValidator, VellumHubMetrics metrics) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordValidator = passwordValidator;
        this.metrics = metrics;
    }

    @Transactional
    public void execute(UserEntity userEntity) {
        if (userEntity == null) throw new AuthDomainException("User can't be null");

        if (userRepository.existsByEmail(userEntity.getEmail())) {
            throw new AuthDomainException("Email already in use");
        }

        String rawPassword = userEntity.getPassword();

        validatePassword(rawPassword);

        userEntity.setPassword(passwordEncoder.encode(rawPassword));

        userRepository.save(userEntity);
        metrics.recordBusinessCounter(VellumHubMetrics.USERS_CREATED, "user_registration", "success");
    }

    private void validatePassword(String password) {
        RuleResult result = passwordValidator.validate(new PasswordData(password));

        if (!result.isValid()) {
            String messages = passwordValidator.getMessages(result).stream()
                    .reduce((m1, m2) -> m1 + ", " + m2)
                    .orElse("Invalid password");

            throw new AuthDomainException("Password invalid: " + messages);
        }
    }

}
