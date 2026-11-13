package io.miragon.blueprint.adapter.inbound.rest;

import io.miragon.blueprint.application.port.inbound.ClarifyReturnUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bike-leasing")
public class ClarifyReturnController {

    private final ClarifyReturnUseCase useCase;

    public ClarifyReturnController(ClarifyReturnUseCase useCase) {
        this.useCase = useCase;
    }

    @Operation(operationId = "clarifyReturn")
    @PostMapping("/{applicationId}/clarify-return")
    public ResponseEntity<Void> clarifyReturn(@PathVariable String applicationId) {
        useCase.clarifyReturn(ApplicationId.of(applicationId));
        return ResponseEntity.accepted().build();
    }
}
