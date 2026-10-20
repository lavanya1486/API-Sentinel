package dev.apisentinel.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() { }

    public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 12, max = 72) String password
    ) { }

    public record LoginRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 72) String password
    ) { }

    public record AuthResponse(String token, String tokenType, long expiresInSeconds, UserSummary user) { }
    public record UserSummary(UUID id, String email, Instant createdAt) { }
    public record AccountResponse(UUID id, String email, Instant createdAt) { }

    public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) String currentPassword,
        @NotBlank @Size(min = 12, max = 72) String newPassword
    ) { }
}
