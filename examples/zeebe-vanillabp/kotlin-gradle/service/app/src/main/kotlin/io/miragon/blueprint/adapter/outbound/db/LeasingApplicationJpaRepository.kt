package io.miragon.blueprint.adapter.outbound.db

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.domain.leasing.LeasingStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface LeasingApplicationJpaRepository : JpaRepository<LeasingApplicationAggregate, String> {
    fun findAllByStatus(status: LeasingStatus, pageable: Pageable): Page<LeasingApplicationAggregate>
    fun findAllByClarificationRequestedAtIsNotNull(): List<LeasingApplicationAggregate>
}
