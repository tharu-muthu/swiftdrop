package com.swiftdrop.dto.identity;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
public record SavedAddressRequest(
    @NotBlank @Size(max=60) String label,
    @NotBlank @Size(max=200) String contactName,
    @NotBlank @Pattern(regexp="(?:\\+94|0)7[0-9]{8}",message="Enter a Sri Lankan mobile number such as 0771234567.") String contactPhone,
    @NotBlank @Size(max=140) String addressLine1,
    @NotNull @Size(max=140) String addressLine2,
    @NotBlank @Size(max=100) String city,
    @NotBlank @Pattern(regexp="[0-9]{5}",message="Enter a five-digit postal code.") String postalCode,
    @NotNull Boolean defaultPickup,
    @NotNull @PositiveOrZero Long version) {
    @JsonAnySetter public void reject(String key,Object value){throw new IllegalArgumentException();}
    @Override public String toString(){return "SavedAddressRequest[redacted]";}
}
