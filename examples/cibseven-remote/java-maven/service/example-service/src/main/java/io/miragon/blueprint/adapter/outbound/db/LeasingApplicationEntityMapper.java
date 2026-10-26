package io.miragon.blueprint.adapter.outbound.db;

import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.ContractId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.Email;
import io.miragon.blueprint.domain.leasing.LeasingApplication;

public final class LeasingApplicationEntityMapper {

    private LeasingApplicationEntityMapper() {
    }

    public static LeasingApplication toDomain(LeasingApplicationEntity entity) {
        String orderId = entity.getOrderId();
        String contractId = entity.getContractId();
        return new LeasingApplication(
                new ApplicationId(entity.getApplicationId()),
                new CustomerName(entity.getCustomerName()),
                new Email(entity.getEmail()),
                entity.getAge(),
                entity.getMonthlyNetIncome(),
                new BikeId(entity.getBikeId()),
                entity.getStatus(),
                entity.getCreatedAt(),
                orderId != null ? new OrderId(orderId) : null,
                contractId != null ? new ContractId(contractId) : null
        );
    }

    public static LeasingApplicationEntity toEntity(LeasingApplication domain) {
        OrderId orderId = domain.orderId();
        ContractId contractId = domain.contractId();
        return new LeasingApplicationEntity(
                domain.id().value(),
                domain.customerName().value(),
                domain.email().value(),
                domain.age(),
                domain.monthlyNetIncome(),
                domain.bikeId().value(),
                domain.status(),
                orderId != null ? orderId.value() : null,
                contractId != null ? contractId.value() : null,
                domain.createdAt()
        );
    }
}
