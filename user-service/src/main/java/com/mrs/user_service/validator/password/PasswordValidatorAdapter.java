package com.mrs.user_service.validator.password;

import org.passay.PasswordData;
import org.passay.PasswordValidator;
import org.passay.RuleResult;
import org.springframework.beans.factory.annotation.Autowired;

public class PasswordValidatorAdapter {

    private final PasswordValidator validator;

    @Autowired
    public PasswordValidatorAdapter(org.passay.PasswordValidator validator) {
        this.validator = validator;
    }

    public boolean isValid(String password) {
        if (password == null || password.isBlank()) {
            return false;
        }

        RuleResult result = validator.validate(new PasswordData(password));

        return result.isValid();
    }
}