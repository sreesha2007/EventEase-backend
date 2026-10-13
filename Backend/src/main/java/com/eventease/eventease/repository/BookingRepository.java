
package com.eventease.eventease.repository;

import com.eventease.eventease.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    boolean existsByCustomerIdAndServiceId(
            Long customerId,
            Long serviceId
    );

    List<Booking> findByServiceIdAndStatusOrderByEventDateAsc(
            Long serviceId,
            String status
    );
}


