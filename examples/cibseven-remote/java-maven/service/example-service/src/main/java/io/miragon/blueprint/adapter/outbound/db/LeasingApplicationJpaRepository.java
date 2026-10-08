package io.miragon.blueprint.adapter.outbound.db;

import io.miragon.blueprint.domain.leasing.LeasingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface LeasingApplicationJpaRepository extends JpaRepository<LeasingApplicationEntity, UUID> {
    Optional<LeasingApplicationEntity> findByApplicationId(UUID id);

    Page<LeasingApplicationEntity> findAllByStatus(LeasingStatus status, Pageable pageable);
}
