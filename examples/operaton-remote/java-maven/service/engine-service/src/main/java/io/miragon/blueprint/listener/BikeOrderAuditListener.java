package io.miragon.blueprint.listener;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.ExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Example {@link ExecutionListener} on the {@code serviceTask_orderBike} service task, wired via
 * {@code camunda:executionListener event="end" delegateExpression="#{bikeOrderAuditListener}"} in the
 * BPMN. It fires <em>after</em> the external {@code orderBike} worker has finished the task and can
 * read the result variable that worker returned, so it simply audit-logs the outcome. No
 * {@code orderId} means the worker left the task through the {@code bikeUnavailable} BPMN error.
 *
 * <p><b>Why it lives in {@code engine-service}, not the worker:</b> unlike a service task, an execution
 * listener has no external-task equivalent — it always runs <em>inside</em> the engine, so the generic
 * host carries the bean (referenced by expression, {@code #{bikeOrderAuditListener}}). The engine
 * deliberately does not depend on the worker, so it has no access to the worker's generated
 * {@code *ProcessApi} — the variable name below is therefore a plain string constant that must stay in
 * sync with the model by hand. A production listener could route through a use case instead of logging.
 */
@Component
public class BikeOrderAuditListener implements ExecutionListener {

    // Kept in sync with `serviceTask_orderBike`'s output variable by hand — the engine does not
    // share the worker's generated variable contract.
    private static final String VAR_ORDER_ID = "orderId";

    private static final Logger log = LoggerFactory.getLogger(BikeOrderAuditListener.class);

    @Override
    public void notify(DelegateExecution execution) {
        Object orderId = execution.getVariable(VAR_ORDER_ID);
        log.info(
                "Bike order finished for application '{}': orderId={}",
                execution.getProcessBusinessKey(),
                orderId);
    }
}
