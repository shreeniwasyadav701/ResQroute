package com.resqroute.dto;

import com.resqroute.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 60) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 72) String password) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record AuthResponse(String token, Long id, String username, Role role, long expiresInMinutes) {
    }

    public record UserResponse(Long id, String username, String email, Role role,
                               boolean enabled, Instant createdAt) {
    }

    public record RoleUpdateRequest(Role role) {
    }

    public record UserListResponse(List<UserResponse> users) {
    }
}
