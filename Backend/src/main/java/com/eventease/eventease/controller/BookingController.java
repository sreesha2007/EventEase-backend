
package com.eventease.eventease.controller;

import com.eventease.eventease.entity.Booking;
import com.eventease.eventease.entity.Customer;
import com.eventease.eventease.entity.ProviderProfile;
import com.eventease.eventease.entity.Service;

import com.eventease.eventease.repository.BookingRepository;
import com.eventease.eventease.repository.CustomerRepository;
import com.eventease.eventease.repository.ProviderProfileRepository;
import com.eventease.eventease.repository.ServiceRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@CrossOrigin(origins = "${app.cors.allowed-origin}")
public class BookingController {

    private final BookingRepository bookingRepository;
    private final ServiceRepository serviceRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final CustomerRepository customerRepository;

    public BookingController(
            BookingRepository bookingRepository,
            ServiceRepository serviceRepository,
            ProviderProfileRepository providerProfileRepository,
            CustomerRepository customerRepository) {

        this.bookingRepository = bookingRepository;
        this.serviceRepository = serviceRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.customerRepository = customerRepository;
    }

    // CUSTOMER: Create a booking request
    @PostMapping
    public ResponseEntity<?> createBooking(@RequestBody Booking booking) {

        if (booking.getCustomerId() == null
                || booking.getServiceId() == null
                || booking.getEventDate() == null) {

            return ResponseEntity.badRequest().body(
                    Map.of("message",
                            "Customer, service and event date are required"));
        }

        Customer customer = customerRepository
                .findById(booking.getCustomerId())
                .orElse(null);

        if (customer == null
                || !"CUSTOMER".equalsIgnoreCase(customer.getRole())) {

            return ResponseEntity.badRequest().body(
                    Map.of("message", "Valid customer account required"));
        }

        Service service = serviceRepository
                .findById(booking.getServiceId())
                .orElse(null);

        if (service == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("message", "Service not found"));
        }

        ProviderProfile profile = providerProfileRepository
                .findById(service.getProviderId())
                .orElse(null);

        if (profile == null
                || !"APPROVED".equalsIgnoreCase(
                        profile.getApprovalStatus())) {

            return ResponseEntity.badRequest().body(
                    Map.of("message",
                            "This service is not currently available"));
        }

        // Prevent the same customer from requesting the same service twice,
        // regardless of date or previous booking status.
        boolean alreadyRequested =
                bookingRepository.existsByCustomerIdAndServiceId(
                        booking.getCustomerId(),
                        booking.getServiceId());

        if (alreadyRequested) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    Map.of("message",
                            "You have already requested this service. "
                                    + "Please check your existing booking status."));
        }

        // A confirmed booking makes the service unavailable on that date.
        List<Booking> confirmedBookings =
                bookingRepository
                        .findByServiceIdAndStatusOrderByEventDateAsc(
                                booking.getServiceId(), "CONFIRMED");

        boolean dateAlreadyBooked = confirmedBookings.stream()
                .anyMatch(existing ->
                        booking.getEventDate().equals(
                                existing.getEventDate()));

        if (dateAlreadyBooked) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    Map.of("message",
                            "This service is already booked on the selected "
                                    + "date. Please choose another date."));
        }

        booking.setStatus("PENDING");
        booking.setRejectionReason(null);

        Booking savedBooking = bookingRepository.save(booking);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedBooking);
    }

    // CUSTOMER: View their booking history
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<?> getCustomerBookings(
            @PathVariable Long customerId) {

        return ResponseEntity.ok(
                bookingRepository
                        .findByCustomerIdOrderByCreatedAtDesc(customerId));
    }

    // Get confirmed dates for a service without exposing customer details
    @GetMapping("/service/{serviceId}/booked-dates")
    public ResponseEntity<?> getBookedDates(
            @PathVariable Long serviceId) {

        if (!serviceRepository.existsById(serviceId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("message", "Service not found"));
        }

        List<String> dates = bookingRepository
                .findByServiceIdAndStatusOrderByEventDateAsc(
                        serviceId, "CONFIRMED")
                .stream()
                .map(booking -> booking.getEventDate().toString())
                .distinct()
                .toList();

        return ResponseEntity.ok(dates);
    }

    // PROVIDER: View booking requests for their services
    @GetMapping("/provider/{customerId}")
    public ResponseEntity<?> getProviderBookings(
            @PathVariable Long customerId) {

        ProviderProfile profile = providerProfileRepository
                .findByCustomerId(customerId)
                .orElse(null);

        if (profile == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("message", "Provider profile not found"));
        }

        List<Long> serviceIds = serviceRepository
                .findByProviderId(profile.getId())
                .stream()
                .map(Service::getId)
                .toList();

        List<Booking> bookings = bookingRepository.findAll()
                .stream()
                .filter(booking ->
                        serviceIds.contains(booking.getServiceId()))
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .toList();

        return ResponseEntity.ok(bookings);
    }

    // PROVIDER: Accept or reject a pending booking request
    @PatchMapping("/{bookingId}/status")
    public ResponseEntity<?> updateBookingStatus(
            @PathVariable Long bookingId,
            @RequestBody Map<String, String> body) {

        String status = body.get("status");

        if (status == null
                || !List.of("CONFIRMED", "REJECTED").contains(status)) {

            return ResponseEntity.badRequest().body(
                    Map.of("message",
                            "Status must be CONFIRMED or REJECTED"));
        }

        Booking booking = bookingRepository
                .findById(bookingId)
                .orElse(null);

        if (booking == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("message", "Booking not found"));
        }

        // Only pending requests can be processed.
        if (!"PENDING".equalsIgnoreCase(booking.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    Map.of("message",
                            "This booking has already been processed."));
        }

        if ("CONFIRMED".equals(status)) {

            List<Booking> confirmedBookings =
                    bookingRepository
                            .findByServiceIdAndStatusOrderByEventDateAsc(
                                    booking.getServiceId(), "CONFIRMED");

            boolean dateConflict = confirmedBookings.stream()
                    .anyMatch(existing ->
                            booking.getEventDate().equals(
                                    existing.getEventDate()));

            if (dateConflict) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        Map.of("message",
                                "Another booking is already confirmed "
                                        + "for this service and date."));
            }

            booking.setStatus("CONFIRMED");
            booking.setRejectionReason(null);

        } else {

            String reason = body.get("rejectionReason");

            if (reason == null || reason.isBlank()) {
                reason = body.get("reason");
            }

            if (reason == null || reason.isBlank()) {
                return ResponseEntity.badRequest().body(
                        Map.of("message",
                                "Please provide a rejection reason."));
            }

            booking.setStatus("REJECTED");
            booking.setRejectionReason(reason.trim());
        }

        return ResponseEntity.ok(bookingRepository.save(booking));
    }
}
