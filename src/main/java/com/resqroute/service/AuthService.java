package com.resqroute.service;

import com.resqroute.dto.AuthDtos.AuthResponse;
import com.resqroute.dto.AuthDtos.LoginRequest;
import com.resqroute.dto.AuthDtos.RegisterRequest;
import com.resqroute.dto.AuthDtos.UserResponse;
import com.resqroute.entity.User;
import com.resqroute.enums.Role;
import com.resqroute.exception.ApiException;
import com.resqroute.repository.UserRepository;
import com.resqroute.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /**
     * Public self-registration always lands in the USER role.
     * Operator and admin accounts are provisioned through the admin API.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (users.existsByUsername(request.username())) {
            throw ApiException.conflict("Username already taken");
        }
        if (users.existsByEmail(request.email())) {
            throw ApiException.conflict("Email already registered");
        }
        User user = new User(request.username().trim(),
                request.email().trim(),
                passwordEncoder.encode(request.password()),
                Role.USER);
        User saved = users.save(user);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        User user = users.findByUsername(request.username())
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (!user.isEnabled()) {
            throw ApiException.forbidden("Account disabled");
        }
        String token = jwtService.generate(user);
        return new AuthResponse(token, user.getId(), user.getUsername(), user.getRole(),
                jwtService.expirationMinutes());
    }

    @Transactional(readOnly = true)
    public UserResponse me(String username) {
        return toResponse(users.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("User not found")));
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getRole(), user.isEnabled(), user.getCreatedAt());
    }
}
