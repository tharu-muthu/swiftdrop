package com.swiftdrop.dto.identity;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftdrop.validation.identity.ValidPassword;
import jakarta.validation.constraints.*;

public final class AccountSecurityRequests {
    private AccountSecurityRequests() { }
    public record ChangeEmail(@NotBlank @Email @Size(max=320) String email,
            @Size(max=256) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String password,
            @Size(max=128) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String reauthToken) {
        @JsonAnySetter public void reject(String key,Object value){throw new IllegalArgumentException();}
        @Override public String toString(){return "ChangeEmail[redacted]";}
    }
    public record ChangePassword(@Size(max=256) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String currentPassword,
            @ValidPassword @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String password,
            @Size(max=128) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String reauthToken) {
        @JsonAnySetter public void reject(String key,Object value){throw new IllegalArgumentException();}
        @Override public String toString(){return "ChangePassword[redacted]";}
    }
    public record ConfirmEmail(@NotBlank @Size(max=128) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String token) {
        @JsonAnySetter public void reject(String key,Object value){throw new IllegalArgumentException();}
        @Override public String toString(){return "ConfirmEmail[redacted]";}
    }
}
