package com.resqroute.service;

import com.resqroute.dto.AuthDtos.RoleUpdateRequest;
import com.resqroute.dto.AuthDtos.UserListResponse;
import com.resqroute.dto.AuthDtos.UserResponse;
import com.resqroute.entity.User;
import com.resqroute.enums.Role;
import com.resqroute.exception.ApiException;
import com.resqroute.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Admin operations over accounts: list, promote/demote, disable. */
@Service
public class AdminUserService {

    private final UserRepository users;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public AdminUserService(UserRepository users,
                            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserListResponse list() {
        List<UserResponse> all = users.findAll().stream().map(AuthService::toResponse).toList();
        return new UserListResponse(all);
    }

    @Transactional
    public UserResponse updateRole(Long userId, RoleUpdateRequest request) {
        if (request.role() == null) {
            throw ApiException.badRequest("Role is required");
        }
        User user = require(userId);
        if (user.getRole() == Role.ADMIN && request.role() != Role.ADMIN) {
            long admins = users.findByRole(Role.ADMIN).size();
            if (admins <= 1) {
                throw ApiException.conflict("Cannot demote the last administrator");
            }
        }
        user.setRole(request.role());
        return AuthService.toResponse(users.save(user));
    }

    @Transactional
    public UserResponse setEnabled(Long userId, boolean enabled) {
        User user = require(userId);
        if (!enabled && user.getRole() == Role.ADMIN && !user.isEnabled()) {
            throw ApiException.conflict("Administrator already disabled");
        }
        user.setEnabled(enabled);
        return AuthService.toResponse(users.save(user));
    }

    @Transactional
    public UserResponse createOperator(String username, String email, String password, Role role) {
        if (role == Role.USER) {
            throw ApiException.badRequest("Use /api/auth/register for standard users");
        }
        if (users.existsByUsername(username)) {
            throw ApiException.conflict("Username already taken");
        }
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("Email already registered");
        }
        User user = new User(username, email, passwordEncoder.encode(password), role);
        return AuthService.toResponse(users.save(user));
    }

    private User require(Long id) {
        return users.findById(id)
                .orElseThrow(() -> ApiException.notFound("User " + id + " not found"));
    }
}
