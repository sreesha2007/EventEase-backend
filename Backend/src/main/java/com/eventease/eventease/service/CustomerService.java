
package com.eventease.eventease.service;

import com.eventease.eventease.entity.Customer;
import com.eventease.eventease.entity.ProviderProfile;
import com.eventease.eventease.repository.CustomerRepository;
import com.eventease.eventease.repository.ProviderProfileRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final ProviderProfileRepository providerProfileRepository;

    public CustomerService(
            CustomerRepository customerRepository,
            ProviderProfileRepository providerProfileRepository) {
        this.customerRepository = customerRepository;
        this.providerProfileRepository = providerProfileRepository;
    }

    @Transactional
    public Customer register(Customer customer) {
        return register(customer, "CUSTOMER");
    }

    @Transactional
    public Customer register(Customer customer, String requestedRole) {
        String role = normalizeRole(requestedRole);

        if (!role.equals("CUSTOMER")
                && !role.equals("SERVICE_PROVIDER")) {
            throw new IllegalArgumentException(
                    "This account role cannot register publicly.");
        }

        if (customer.getEmail() == null
                || customer.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }

        if (customer.getPassword() == null
                || customer.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required.");
        }

        if (customerRepository.existsByEmail(customer.getEmail())) {
            throw new IllegalArgumentException(
                    "Email is already registered.");
        }

        customer.setRole(role);

        Customer savedCustomer = customerRepository.save(customer);

        if (role.equals("SERVICE_PROVIDER")) {
            ProviderProfile profile = new ProviderProfile();
            profile.setCustomerId(savedCustomer.getId());
            profile.setApprovalStatus("PENDING");

            providerProfileRepository.save(profile);
        }

        return savedCustomer;
    }

    public Customer login(String email, String password) {
        return login(email, password, "CUSTOMER");
    }

    public Customer login(
            String email,
            String password,
            String requestedRole) {

        String role = normalizeRole(requestedRole);

        Customer customer = customerRepository
                .findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid email or password."));

        if (customer.getPassword() == null
                || !customer.getPassword().equals(password)) {
            throw new IllegalArgumentException(
                    "Invalid email or password.");
        }

        if (!role.equals(customer.getRole())) {
            throw new IllegalArgumentException(
                    "Invalid email or password for this account type.");
        }

        return customer;
    }

    public ProviderProfile getProviderProfile(Long customerId) {
        return providerProfileRepository
                .findByCustomerId(customerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider profile not found."));
    }

    private String normalizeRole(String requestedRole) {
        if (requestedRole == null || requestedRole.isBlank()) {
            throw new IllegalArgumentException("Account role is required.");
        }

        String role = requestedRole
                .trim()
                .toUpperCase()
                .replace(' ', '_');

        if (!role.equals("CUSTOMER")
                && !role.equals("SERVICE_PROVIDER")
                && !role.equals("ADMIN")) {
            throw new IllegalArgumentException("Invalid account role.");
        }

        return role;
    }
}
