
package com.eventease.eventease.controller;

import com.eventease.eventease.entity.Customer;
import com.eventease.eventease.service.CustomerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/customers")
@CrossOrigin(origins = "http://localhost:5173")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody Customer customer
    ) {
        try {
            Customer savedCustomer =
                    customerService.register(customer, "CUSTOMER");

            savedCustomer.setPassword(null);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(savedCustomer);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody Map<String, String> request
    ) {
        try {
            String requestedRole = request.getOrDefault(
                    "role",
                    "CUSTOMER"
            );

            Customer customer = customerService.login(
                    request.get("email"),
                    request.get("password"),
                    requestedRole
            );

            customer.setPassword(null);
            return ResponseEntity.ok(customer);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(e.getMessage());
        }
    }
}
