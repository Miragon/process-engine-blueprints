package io.miragon.blueprint.adapter.outbound.db;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeasingApplicationJpaRepository extends JpaRepository<LeasingApplicationAggregate, String> {

    Page<LeasingApplicationAggregate> findAllByStatus(LeasingStatus status, Pageable pageable);

    List<LeasingApplicationAggregate> findAllByClarificationRequestedAtIsNotNull();
}
