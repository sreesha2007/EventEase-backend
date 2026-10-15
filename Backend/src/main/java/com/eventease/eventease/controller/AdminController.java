
package com.eventease.eventease.controller;

import com.eventease.eventease.entity.Customer;
import com.eventease.eventease.entity.ProviderProfile;
import com.eventease.eventease.entity.Service;
import com.eventease.eventease.repository.CustomerRepository;
import com.eventease.eventease.repository.ProviderProfileRepository;
import com.eventease.eventease.repository.ServiceRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "${app.cors.allowed-origin}")
public class AdminController {

    private final CustomerRepository customerRepository;
    private final ProviderProfileRepository profileRepository;
    private final ServiceRepository serviceRepository;

    public AdminController(
            CustomerRepository customerRepository,
            ProviderProfileRepository profileRepository,
            ServiceRepository serviceRepository) {

        this.customerRepository = customerRepository;
        this.profileRepository = profileRepository;
        this.serviceRepository = serviceRepository;
    }

    // Get all providers
    @GetMapping("/providers")
    public List<Map<String, Object>> getProviders() {
        return profileRepository.findAll()
                .stream()
                .map(this::toProviderResponse)
                .toList();
    }

    // Get providers waiting for approval
    @GetMapping("/providers/pending")
    public List<Map<String, Object>> getPendingProviders() {
        return profileRepository.findByApprovalStatus("PENDING")
                .stream()
                .map(this::toProviderResponse)
                .toList();
    }

    // Approve a provider
    @PostMapping("/providers/{customerId}/approve")
    @Transactional
    public ResponseEntity<?> approveProvider(
            @PathVariable Long customerId) {

        ProviderProfile profile = profileRepository
                .findByCustomerId(customerId)
                .orElse(null);

        if (profile == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Provider profile not found."));
        }

        Customer customer = customerRepository.findById(customerId)
                .orElse(null);

        if (customer == null
                || !"SERVICE_PROVIDER".equals(customer.getRole())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid provider account."));
        }

        profile.setApprovalStatus("APPROVED");
        profile.setRejectionReason(null);
        profileRepository.save(profile);

        return ResponseEntity.ok(Map.of(
                "message", "Provider approved successfully.",
                "status", "APPROVED"
        ));
    }

    // Reject a provider with a reason
    @PostMapping("/providers/{customerId}/reject")
    @Transactional
    public ResponseEntity<?> rejectProvider(
            @PathVariable Long customerId,
            @RequestBody Map<String, String> request) {

        String reason = request.get("reason");

        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message", "Please provide a rejection reason."
                    ));
        }

        ProviderProfile profile = profileRepository
                .findByCustomerId(customerId)
                .orElse(null);

        if (profile == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Provider profile not found."));
        }

        Customer customer = customerRepository.findById(customerId)
                .orElse(null);

        if (customer == null
                || !"SERVICE_PROVIDER".equals(customer.getRole())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid provider account."));
        }

        profile.setApprovalStatus("REJECTED");
        profile.setRejectionReason(reason.trim());
        profileRepository.save(profile);

        return ResponseEntity.ok(Map.of(
                "message", "Provider rejected.",
                "status", "REJECTED",
                "reason", reason.trim()
        ));
    }

    // Provider information for the admin dashboard
    private Map<String, Object> toProviderResponse(
            ProviderProfile profile) {

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("customerId", profile.getCustomerId());
        result.put("businessName", profile.getBusinessName());
        result.put("approvalStatus", profile.getApprovalStatus());
        result.put("rejectionReason", profile.getRejectionReason());

        customerRepository.findById(profile.getCustomerId())
                .ifPresent(customer -> {
                    result.put("name", customer.getName());
                    result.put("email", customer.getEmail());
                });

        return result;
    }

    // Admin login
    @PostMapping("/login")
    public ResponseEntity<?> adminLogin(
            @RequestBody Map<String, String> request) {

        String email = request.get("email");
        String password = request.get("password");

        if (email == null || password == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message", "Email and password are required."
                    ));
        }

        Customer admin = customerRepository.findByEmail(email)
                .orElse(null);

        if (admin == null
                || !"ADMIN".equals(admin.getRole())
                || admin.getPassword() == null
                || !admin.getPassword().equals(password)) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message", "Invalid admin email or password."
                    ));
        }

        return ResponseEntity.ok(Map.of(
                "id", admin.getId(),
                "name", admin.getName(),
                "email", admin.getEmail(),
                "role", admin.getRole()
        ));
    }

    // Get all registered customers
    @GetMapping("/customers")
    public List<Map<String, Object>> getCustomers() {
        return customerRepository.findAll()
                .stream()
                .filter(customer -> "CUSTOMER".equals(customer.getRole()))
                .map(customer -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", customer.getId());
                    result.put("name", customer.getName());
                    result.put("email", customer.getEmail());
                    result.put("role", customer.getRole());
                    return result;
                })
                .toList();
    }

    // Get all services with the provider managing each service
    @GetMapping("/services")
    public List<Map<String, Object>> getAllServices() {
        return serviceRepository.findAll()
                .stream()
                .map(service -> {
                    Map<String, Object> result = new LinkedHashMap<>();

                    result.put("id", service.getId());
                    result.put("category", service.getCategory());
                    result.put("title", service.getTitle());
                    result.put("description", service.getDescription());
                    result.put("price", service.getPrice());
                    result.put("imageUrl", service.getImageUrl());

                    ProviderProfile profile = profileRepository
                            .findById(service.getProviderId())
                            .orElse(null);

                    if (profile != null) {
                        result.put("businessName", profile.getBusinessName());
                        result.put("providerCustomerId", profile.getCustomerId());
                        result.put("approvalStatus", profile.getApprovalStatus());

                        customerRepository.findById(profile.getCustomerId())
                                .ifPresent(customer -> {
                                    result.put("providerName", customer.getName());
                                    result.put("providerEmail", customer.getEmail());
                                });
                    } else {
                        result.put("businessName", "Unknown provider");
                        result.put("providerName", "Unknown provider");
                        result.put("providerEmail", "—");
                        result.put("approvalStatus", "UNKNOWN");
                    }

                    return result;
                })
                .toList();
    }
}
