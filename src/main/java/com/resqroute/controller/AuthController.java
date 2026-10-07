package com.resqroute.controller;

import com.resqroute.dto.AuthDtos.AuthResponse;
import com.resqroute.dto.AuthDtos.LoginRequest;
import com.resqroute.dto.AuthDtos.RegisterRequest;
import com.resqroute.dto.AuthDtos.UserResponse;
import com.resqroute.dto.ResourceDtos.ApiResponse;
import com.resqroute.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ApiResponse.ok("Registration successful", user);
    }

    @PostMapping("/login")
    public ApiResponse login(@Valid @RequestBody LoginRequest request) {
        AuthResponse auth = authService.login(request);
        return ApiResponse.ok("Login successful", auth);
    }

    @GetMapping("/me")
    public ApiResponse me(@AuthenticationPrincipal UserDetails principal) {
        return ApiResponse.ok("Current user", authService.me(principal.getUsername()));
    }
}
