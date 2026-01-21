package com.mrs.user_service.share.validator.password;

import org.passay.*;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class PasswordValidatorAdapter extends PasswordValidator {

    private final static List<Rule> RULES = Arrays.asList(
            new LengthRule(8, 30),

            new CharacterRule(EnglishCharacterData.UpperCase, 1),
            new CharacterRule(EnglishCharacterData.LowerCase, 1),
            new CharacterRule(EnglishCharacterData.Digit, 1),
            new CharacterRule(EnglishCharacterData.Special, 1),

            new WhitespaceRule(),
            new IllegalSequenceRule(EnglishSequenceData.Alphabetical, 5, false),
            new IllegalSequenceRule(EnglishSequenceData.Numerical, 5, false),
            new IllegalSequenceRule(EnglishSequenceData.USQwerty, 5, false)
    );


    public PasswordValidatorAdapter() {
        super(RULES);
    }


}