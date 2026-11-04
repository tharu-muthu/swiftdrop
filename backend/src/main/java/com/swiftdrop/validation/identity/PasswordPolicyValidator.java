package com.swiftdrop.validation.identity;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        int length = value == null ? 0 : value.codePointCount(0, value.length());
        String message = length == 0 ? "Enter a password."
                : length < 8 ? "Password must contain at least 8 characters."
                : length > 128 ? "Password must contain no more than 128 characters." : null;
        if (message == null) return true;
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
