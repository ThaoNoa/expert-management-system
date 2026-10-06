package com.npcore.ems.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() {}

    public record UserDto(UUID id, String username, String email, String fullName, UUID departmentId,
                          String departmentName, String position, String status, List<String> roleCodes,
                          OffsetDateTime lastLoginAt, OffsetDateTime createdAt) {
        static UserDto from(User u) {
            return new UserDto(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(),
                    u.getDepartment() == null ? null : u.getDepartment().getId(),
                    u.getDepartment() == null ? null : u.getDepartment().getName(),
                    u.getPosition(), u.getStatus(), u.getRoles().stream().map(Role::getCode).sorted().toList(),
                    u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "chỉ gồm chữ không dấu, số, . _ -") String username,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 255) String fullName,
            UUID departmentId,
            @Size(max = 255) String position,
            @NotBlank String password,
            @NotEmpty List<String> roleCodes) {}

    public record UpdateUserRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 255) String fullName,
            UUID departmentId,
            @Size(max = 255) String position,
            @NotEmpty List<String> roleCodes) {}

    public record ResetPasswordRequest(@NotBlank String newPassword) {}
}
