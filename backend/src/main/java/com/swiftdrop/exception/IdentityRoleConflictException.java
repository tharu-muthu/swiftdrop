package com.swiftdrop.exception;

public class IdentityRoleConflictException extends RuntimeException {
    public IdentityRoleConflictException() { super("The account or role changed. Reload before trying again."); }
}
