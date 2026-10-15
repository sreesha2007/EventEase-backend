
package com.eventease.eventease.controller;

import com.eventease.eventease.entity.Customer;
import com.eventease.eventease.entity.ProviderProfile;
import com.eventease.eventease.service.CustomerService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/providers")
@CrossOrigin(origins = "${app.cors.allowed-origin}")
public class ProviderController {

    private final CustomerService customerService;

    public ProviderController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody Customer customer) {
        try {
            Customer saved = customerService.register(
                    customer, "SERVICE_PROVIDER");

            saved.setPassword(null);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message",
                            "Registration successful. Await admin approval.",
                            "providerId", saved.getId(),
                            "name", saved.getName(),
                            "email", saved.getEmail(),
                            "role", saved.getRole(),
                            "approvalStatus", "PENDING"
                    ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody Map<String, String> request) {
        try {
            Customer provider = customerService.login(
                    request.get("email"),
                    request.get("password"),
                    "SERVICE_PROVIDER");

            ProviderProfile profile =
                    customerService.getProviderProfile(provider.getId());

            String status = profile.getApprovalStatus();

            if ("PENDING".equalsIgnoreCase(status)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "status", "PENDING",
                                "message",
                                "Your account is awaiting admin approval. You can access your dashboard once your account is approved."
                        ));
            }

            if ("REJECTED".equalsIgnoreCase(status)) {
                String reason = profile.getRejectionReason();

                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "status", "REJECTED",
                                "message", "Your provider registration was rejected.",
                                "rejectionReason",
                                reason == null || reason.isBlank()
                                        ? "No reason was provided by the admin."
                                        : reason
                        ));
            }

            if (!"APPROVED".equalsIgnoreCase(status)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "status", "PENDING",
                                "message",
                                "Your account is awaiting admin approval."
                        ));
            }

            provider.setPassword(null);

            return ResponseEntity.ok(provider);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
