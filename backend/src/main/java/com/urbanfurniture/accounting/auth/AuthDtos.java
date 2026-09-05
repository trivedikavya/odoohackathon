package com.urbanfurniture.accounting.auth;

import com.urbanfurniture.accounting.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record LoginResponse(
            String token,
            long expiresInMs,
            UserProfile user) {
    }

    public record UserProfile(
            Long id,
            String email,
            String fullName,
            Role role,
            Long contactId) {
    }

    /** Admin-only user creation. */
    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 180) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 150) String fullName,
            @NotNull Role role,
            /** Required when {@code role} is CONTACT; must be null otherwise. */
            Long contactId) {
    }

    public record UpdateUserRequest(
            @Size(max = 150) String fullName,
            Role role,
            Boolean active) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record UserResponse(
            Long id,
            String email,
            String fullName,
            Role role,
            Long contactId,
            Boolean active) {
    }
}
