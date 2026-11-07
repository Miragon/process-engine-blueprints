package io.miragon.blueprint.adapter.inbound.rest

import io.miragon.blueprint.application.port.inbound.ClarifyReturnUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/bike-leasing")
class ClarifyReturnController(
    private val useCase: ClarifyReturnUseCase,
) {

    @Operation(operationId = "clarifyReturn")
    @PostMapping("/{applicationId}/clarify-return")
    fun clarifyReturn(@PathVariable applicationId: String): ResponseEntity<Void> {
        useCase.clarifyReturn(ApplicationId.of(applicationId))
        return ResponseEntity.accepted().build()
    }
}
