
package com.eventease.eventease.repository;

import com.eventease.eventease.entity.ProviderProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderProfileRepository
        extends JpaRepository<ProviderProfile, Long> {

    Optional<ProviderProfile> findByCustomerId(Long customerId);

    List<ProviderProfile> findByApprovalStatus(String approvalStatus);
}

