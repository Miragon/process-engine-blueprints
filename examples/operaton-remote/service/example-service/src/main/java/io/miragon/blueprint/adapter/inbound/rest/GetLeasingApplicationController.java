package io.miragon.blueprint.adapter.inbound.rest;

import io.miragon.blueprint.application.port.inbound.GetLeasingApplicationQuery;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
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
        GetLeasingApplicationQuery.Result result = query.byId(ApplicationId.of(applicationId));
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toDto(result));
    }

    private static LeasingApplicationDto toDto(GetLeasingApplicationQuery.Result result) {
        LeasingApplication application = result.application();
        return new LeasingApplicationDto(
                application.id().value().toString(),
                application.customerName().value(),
                application.email().value(),
                application.bikeId().value(),
                // resolved from the bike portfolio, not carried on the application
                result.bikeModel(),
                application.status().name(),
                application.orderId() != null ? application.orderId().value() : null,
                application.contractId() != null ? application.contractId().value() : null);
    }

    public record LeasingApplicationDto(
            String applicationId,
            String customerName,
            String email,
            String bikeId,
            String bikeModel,
            String status,
            String orderId,
            String contractId) {
    }
}
