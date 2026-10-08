package io.miragon.blueprint.listener

import mu.KotlinLogging
import org.cibseven.bpm.engine.delegate.DelegateExecution
import org.cibseven.bpm.engine.delegate.ExecutionListener
import org.springframework.stereotype.Component

/**
 * Example [ExecutionListener] on the `serviceTask_orderBike` service task, wired via
 * `camunda:executionListener event="end" delegateExpression="#{bikeOrderAuditListener}"` in the BPMN.
 * It fires *after* the external `orderBike` worker has completed the task and can read the result
 * variables that worker returned, so it simply audit-logs the outcome.
 *
 * **Why it lives in `engine-service`, not the worker:** unlike a service task, an execution listener
 * has no external-task equivalent — it always runs *inside* the engine, so the generic host carries the
 * bean (referenced by expression, `#{bikeOrderAuditListener}`). The engine deliberately does not depend
 * on the worker, so it has no access to the worker's generated `*ProcessApi` — the variable names below
 * are therefore plain string constants that must stay in sync with the model by hand. A production
 * listener could route through a use case instead of logging.
 */
@Component
class BikeOrderAuditListener : ExecutionListener {

    private val log = KotlinLogging.logger {}

    override fun notify(execution: DelegateExecution) {
        val orderId = execution.getVariable(VAR_ORDER_ID)
        val bikeAvailable = execution.getVariable(VAR_BIKE_AVAILABLE)
        log.info {
            "Bike order finished for application '${execution.processBusinessKey}': " +
                "orderId=$orderId, bikeAvailable=$bikeAvailable"
        }
    }

    private companion object {
        // Kept in sync with `serviceTask_orderBike`'s output variables by hand — the engine does not
        // share the worker's generated variable contract.
        const val VAR_ORDER_ID = "orderId"
        const val VAR_BIKE_AVAILABLE = "bikeAvailable"
    }
}
