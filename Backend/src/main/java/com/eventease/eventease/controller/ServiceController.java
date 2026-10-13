
package com.eventease.eventease.controller;

import com.eventease.eventease.entity.Service;
import com.eventease.eventease.entity.Customer;
import com.eventease.eventease.entity.ProviderProfile;
import com.eventease.eventease.repository.ServiceRepository;
import com.eventease.eventease.repository.ProviderProfileRepository;
import com.eventease.eventease.repository.CustomerRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/services")
@CrossOrigin(origins = "http://localhost:5173")
public class ServiceController {

    private final ServiceRepository serviceRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final CustomerRepository customerRepository;

    public ServiceController(
            ServiceRepository serviceRepository,
            ProviderProfileRepository providerProfileRepository,
            CustomerRepository customerRepository) {

        this.serviceRepository = serviceRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.customerRepository = customerRepository;
    }

    // PUBLIC: Customers see services from approved providers only
    @GetMapping
    public List<Map<String, Object>> getServices() {

        return serviceRepository.findAll().stream()
                .filter(service -> providerProfileRepository
                        .findById(service.getProviderId())
                        .map(profile -> "APPROVED".equalsIgnoreCase(
                                profile.getApprovalStatus()))
                        .orElse(false))
                .map(service -> {
                    Map<String, Object> result = new LinkedHashMap<>();

                    result.put("id", service.getId());
                    result.put("name", service.getCategory());
                    result.put("category", service.getCategory());
                    result.put("providerName", service.getTitle());
                    result.put("description", service.getDescription());
                    result.put("price", service.getPrice());
                    result.put("image", service.getImageUrl());

                    return result;
                })
                .toList();
    }

    // PROVIDER: View their services.
    // Pending providers can manage their services too.
    // Customers will not see them until the profile is approved.
    @GetMapping("/provider/{providerId}")
    public ResponseEntity<?> getProviderServices(
            @PathVariable Long providerId) {

        ProviderProfile profile = providerProfileRepository
                .findByCustomerId(providerId)
                .orElse(null);

        if (profile == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message",
                            "Provider profile not found."));
        }

        return ResponseEntity.ok(
                serviceRepository.findByProviderId(profile.getId()));
    }

    // PROVIDER: Add a service
    @PostMapping("/provider/{providerId}")
    public ResponseEntity<?> createService(
            @PathVariable Long providerId,
            @RequestBody Service service) {

        Customer customer = customerRepository
                .findById(providerId)
                .orElse(null);

        if (customer == null
                || !"SERVICE_PROVIDER".equalsIgnoreCase(
                        customer.getRole())) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message",
                            "A service provider account is required."));
        }

        ProviderProfile profile = providerProfileRepository
                .findByCustomerId(providerId)
                .orElse(null);

        if (profile == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message",
                            "Please complete your provider profile first."));
        }

        if (service.getTitle() == null
                || service.getTitle().isBlank()
                || service.getCategory() == null
                || service.getCategory().isBlank()
                || service.getPrice() == null
                || service.getPrice().signum() < 0) {

            return ResponseEntity.badRequest()
                    .body(Map.of("message",
                            "Enter a service name, category and valid price."));
        }

        service.setProviderId(profile.getId());
        service.setTitle(service.getTitle().trim());
        service.setCategory(service.getCategory().trim());

        if (service.getDescription() != null) {
            service.setDescription(service.getDescription().trim());
        }

        if (service.getImageUrl() != null) {
            service.setImageUrl(service.getImageUrl().trim());
        }

        Service savedService = serviceRepository.save(service);

        String message = "APPROVED".equalsIgnoreCase(
                profile.getApprovalStatus())
                ? "Service added successfully."
                : "Service saved. It will become visible to customers after your provider profile is approved.";

        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of(
                        "message", message,
                        "service", savedService
                ));
    }
}
