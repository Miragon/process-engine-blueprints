package io.miragon.blueprint.adapter.inbound.rest;

import io.miragon.blueprint.application.port.inbound.GetLeasingApplicationQuery;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.ContractId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bike-leasing")
public class GetLeasingApplicationController {

    private final GetLeasingApplicationQuery query;

    public GetLeasingApplicationController(GetLeasingApplicationQuery query) {
        this.query = query;
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<LeasingApplicationDto> byId(@PathVariable String applicationId) {
        return query.byId(ApplicationId.of(applicationId))
                .map(result -> ResponseEntity.ok(toDto(result)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Schema(requiredProperties = {"applicationId", "customerName", "email", "bikeId", "status"})
    public record LeasingApplicationDto(
            String applicationId,
            String customerName,
            String email,
            String bikeId,
            @Schema(nullable = true) @Nullable String bikeModel,
            String status,
            @Schema(nullable = true) @Nullable String orderId,
            @Schema(nullable = true) @Nullable String contractId
    ) {
    }

    private static LeasingApplicationDto toDto(GetLeasingApplicationQuery.Result result) {
        LeasingApplication application = result.application();
        OrderId orderId = application.orderId();
        ContractId contractId = application.contractId();
        return new LeasingApplicationDto(
                application.id().value().toString(),
                application.customerName().value(),
                application.email().value(),
                application.bikeId().value(),
                // resolved from the bike portfolio, not carried on the application
                result.bikeModel(),
                application.status().name(),
                orderId != null ? orderId.value() : null,
                contractId != null ? contractId.value() : null);
    }
}
