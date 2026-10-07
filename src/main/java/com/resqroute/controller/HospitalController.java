package com.resqroute.controller;

import com.resqroute.dto.ResourceDtos.ApiResponse;
import com.resqroute.dto.ResourceDtos.HospitalRequest;
import com.resqroute.service.HospitalService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hospitals")
public class HospitalController {

    private final HospitalService hospitalService;

    public HospitalController(HospitalService hospitalService) {
        this.hospitalService = hospitalService;
    }

    @GetMapping
    public ApiResponse list() {
        return ApiResponse.ok("Hospitals", hospitalService.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse create(@Valid @RequestBody HospitalRequest request) {
        return ApiResponse.ok("Hospital created", hospitalService.create(request));
    }

    @PatchMapping("/{id}/beds")
    @PreAuthorize("hasAnyRole('ADMIN','EMERGENCY_OPERATOR')")
    public ApiResponse updateBeds(@PathVariable Long id, @RequestParam int available) {
        return ApiResponse.ok("Bed count updated", hospitalService.updateBeds(id, available));
    }

    /** Ranks hospitals by ETA for an incident or an arbitrary intersection. */
    @GetMapping("/recommend")
    public ApiResponse recommend(@RequestParam(required = false) Long incidentId,
                                 @RequestParam(required = false) Long fromNodeId,
                                 @RequestParam(required = false) Integer minBeds,
                                 @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok("Hospital recommendations",
                hospitalService.recommend(incidentId, fromNodeId, minBeds, Math.min(10, Math.max(1, limit))));
    }
}
