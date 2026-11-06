package com.swiftdrop.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Future M3 quotation input; no pricing calculation or endpoint is provided by M4. */
public record AddressContactRequest(
        @NotBlank(message = "Full name is required.") @Size(max = 120, message = "Full name must be at most 120 characters.") String fullName,
        @NotBlank(message = "Contact number is required.") @Pattern(regexp = "^(?:\\+94|0)7\\d{8}$", message = "Enter a valid Sri Lankan mobile number.") String contactNumber,
        @NotBlank(message = "Address is required.") @Size(max = 500, message = "Address must be at most 500 characters.") String address,
        @NotBlank(message = "City is required.") @Size(max = 100, message = "City must be at most 100 characters.") String city,
        @NotBlank(message = "Postal code is required.") @Pattern(regexp = "^\\d{5}$", message = "Postal code must have five digits.") String postalCode) { }
