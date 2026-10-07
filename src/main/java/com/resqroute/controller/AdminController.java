package com.resqroute.controller;

import com.resqroute.dto.AuthDtos.RoleUpdateRequest;
import com.resqroute.dto.ResourceDtos.ApiResponse;
import com.resqroute.enums.Role;
import com.resqroute.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminUserService adminUserService;

    public AdminController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/users")
    public ApiResponse users() {
        return ApiResponse.ok("Users", adminUserService.list());
    }

    @PostMapping("/users/{id}/role")
    public ApiResponse updateRole(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        return ApiResponse.ok("Role updated", adminUserService.updateRole(id, request));
    }

    @PostMapping("/users/{id}/enabled")
    public ApiResponse setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return ApiResponse.ok("Account state updated", adminUserService.setEnabled(id, enabled));
    }

    /** Provision an operator account (TRAFFIC_OPERATOR / EMERGENCY_OPERATOR / ADMIN). */
    @PostMapping("/operators")
    public ApiResponse createOperator(@RequestBody OperatorRequest request) {
        return ApiResponse.ok("Operator created",
                adminUserService.createOperator(request.username(), request.email(),
                        request.password(), request.role()));
    }

    public record OperatorRequest(String username, String email, String password, Role role) {
    }
}
