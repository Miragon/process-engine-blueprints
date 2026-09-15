package io.miragon.blueprint.listener;

import org.operaton.bpm.engine.delegate.DelegateTask;
import org.operaton.bpm.engine.delegate.TaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Example {@link TaskListener} on the {@code userTask_clarifyAlternative} user task, wired via
 * {@code camunda:taskListener event="create" delegateExpression="#{clarifyAlternativeTaskListener}"} in
 * the BPMN. It fires when the out-of-stock branch parks the human task, and simply audit-logs that
 * manual clarification is required — the human-task counterpart to {@link BikeOrderAuditListener}.
 *
 * <p>Like the execution listener, it runs <em>inside</em> the engine (task listeners have no
 * external-task equivalent), so the bean lives in the generic {@code engine-service} host and is
 * referenced by expression ({@code #{clarifyAlternativeTaskListener}}). A production listener could
 * notify the customer or route through a use case instead of logging.
 */
@Component
public class ClarifyAlternativeTaskListener implements TaskListener {

    private static final Logger log = LoggerFactory.getLogger(ClarifyAlternativeTaskListener.class);

    @Override
    public void notify(DelegateTask delegateTask) {
        log.info(
                "Manual clarification required for application '{}': task '{}' created",
                delegateTask.getExecution().getProcessBusinessKey(),
                delegateTask.getName());
    }
}
