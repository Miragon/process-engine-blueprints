package io.miragon.blueprint.listener

import mu.KotlinLogging
import org.cibseven.bpm.engine.delegate.DelegateTask
import org.cibseven.bpm.engine.delegate.TaskListener
import org.springframework.stereotype.Component

/**
 * Example [TaskListener] on the `userTask_clarifyAlternative` user task, wired via
 * `camunda:taskListener event="create" delegateExpression="#{clarifyAlternativeTaskListener}"` in the
 * BPMN. It fires when the out-of-stock branch parks the human task, and simply audit-logs that manual
 * clarification is required — the human-task counterpart to [BikeOrderAuditListener].
 *
 * Like the execution listener, it runs *inside* the engine (task listeners have no external-task
 * equivalent), so the bean lives in the generic `engine-service` host and is referenced by expression
 * (`#{clarifyAlternativeTaskListener}`). A production listener could notify the customer or route
 * through a use case instead of logging.
 */
@Component
class ClarifyAlternativeTaskListener : TaskListener {

    private val log = KotlinLogging.logger {}

    override fun notify(delegateTask: DelegateTask) {
        log.info {
            "Manual clarification required for application '${delegateTask.execution.processBusinessKey}': " +
                "task '${delegateTask.name}' created"
        }
    }
}
