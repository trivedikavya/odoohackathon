package com.urbanfurniture.accounting.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            /** Login ID or email — both are unique, so either identifies the account. */
            @NotBlank String identifier,
            @NotBlank String password) {
    }

    /**
     * Self-registration.
     * <p>
     * Choosing SELLER or VENDOR provisions a full set of books. Choosing
     * CUSTOMER does not, because customers buy and pay but keep no
     * accounts of their own.
     */
    public record RegisterRequest(
            @NotBlank @Size(min = 6, max = 12) String loginId,
            @NotBlank @Email @Size(max = 180) String email,
            @NotBlank String password,
            @NotBlank @Size(max = 150) String fullName,
            @NotNull PartyType partyType,
            /** The trading name. Defaults to the person's name if omitted. */
            @Size(max = 180) String organisationName,
            @Size(max = 20) String gstin,
            @Size(max = 255) String addressLine,
            @Size(max = 100) String city,
            /** Drives the CGST/SGST versus IGST decision on every deal. */
            @Size(max = 100) String state,
            @Size(max = 20) String pincode,
            @Size(max = 30) String phone) {
    }

    public record UserProfile(
            Long id,
            String loginId,
            String email,
            String fullName,
            AccessLevel accessLevel,
            Long partyId,
            PartyType partyType,
            String partyName,
            /** Null for customers. */
            Long bookId) {
    }

    public record LoginResponse(
            String token,
            long expiresInMs,
            UserProfile user) {
    }

    public record AvailabilityResponse(boolean available, String message) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank String newPassword) {
    }

    /** Admin creating a colleague inside their own party. */
    public record CreateStaffRequest(
            @NotBlank @Size(min = 6, max = 12) String loginId,
            @NotBlank @Email @Size(max = 180) String email,
            @NotBlank String password,
            @NotBlank @Size(max = 150) String fullName,
            @NotNull AccessLevel accessLevel) {
    }

    public record UserResponse(
            Long id,
            String loginId,
            String email,
            String fullName,
            AccessLevel accessLevel,
            Boolean active) {
    }
}
