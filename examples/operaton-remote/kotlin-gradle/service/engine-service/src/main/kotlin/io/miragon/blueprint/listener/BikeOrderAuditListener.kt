package io.miragon.blueprint.listener

import mu.KotlinLogging
import org.operaton.bpm.engine.delegate.DelegateExecution
import org.operaton.bpm.engine.delegate.ExecutionListener
import org.springframework.stereotype.Component

/**
 * Example [ExecutionListener] on the `serviceTask_orderBike` service task, wired via
 * `camunda:executionListener event="end" delegateExpression="#{bikeOrderAuditListener}"` in the BPMN.
 * It fires *after* the external `orderBike` worker has finished the task and can read the result
 * variable that worker returned, so it simply audit-logs the outcome. No `orderId` means the worker
 * left the task through the `bikeUnavailable` BPMN error.
 *
 * **Why it lives in `engine-service`, not the worker:** unlike a service task, an execution listener
 * has no external-task equivalent — it always runs *inside* the engine, so the generic host carries the
 * bean (referenced by expression, `#{bikeOrderAuditListener}`). The engine deliberately does not depend
 * on the worker, so it has no access to the worker's generated `*ProcessApi` — the variable name below
 * is therefore a plain string constant that must stay in sync with the model by hand. A production
 * listener could route through a use case instead of logging.
 */
@Component
class BikeOrderAuditListener : ExecutionListener {

    private val log = KotlinLogging.logger {}

    override fun notify(execution: DelegateExecution) {
        val orderId = execution.getVariable(VAR_ORDER_ID)
        log.info {
            "Bike order finished for application '${execution.processBusinessKey}': " +
                "orderId=$orderId"
        }
    }

    private companion object {
        // Kept in sync with `serviceTask_orderBike`'s output variable by hand — the engine does not
        // share the worker's generated variable contract.
        const val VAR_ORDER_ID = "orderId"
    }
}
