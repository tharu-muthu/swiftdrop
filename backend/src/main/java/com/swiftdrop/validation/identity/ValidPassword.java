package com.swiftdrop.validation.identity;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordPolicyValidator.class)
public @interface ValidPassword {
    String message() default "Password must contain 8–128 characters.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
