package io.miragon.blueprint.process.model;

import io.miragon.bpmn.domain.shared.ServiceTaskDefinition;
import io.miragon.bpmn.domain.validation.SingleModelValidationRule;
import io.miragon.bpmn.domain.validation.model.Severity;
import io.miragon.bpmn.domain.validation.model.SingleModelValidationContext;
import io.miragon.bpmn.domain.validation.model.ValidationViolation;
import java.util.List;

/**
 * Custom bpmn-to-code validation rule: every <em>implemented</em> service task must be an
 * <strong>external task</strong> ({@code camunda:type="external"} with a {@code camunda:topic}) — i.e.
 * no delegate expressions, {@code camunda:class} or plain {@code ${...}} expressions.
 *
 * <p>This is the remote counterpart to the embedded blueprint's delegate-expression rule: all
 * service-task logic runs in the separate worker ({@code example-service}) and is consumed over the
 * engine's external-task REST API, never as engine-hosted JavaDelegates. Service tasks with no
 * implementation at all are left to the built-in {@code MISSING_SERVICE_TASK_IMPLEMENTATION} rule.
 */
public class ServiceTaskExternalTopicRule implements SingleModelValidationRule {

    private static final String EXTERNAL_TASK_KIND = "EXTERNAL_TASK";

    private final String id = "SERVICE_TASK_MUST_USE_EXTERNAL_TOPIC";

    private final Severity severity = Severity.ERROR;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Severity getSeverity() {
        return severity;
    }

    @Override
    public List<ValidationViolation> validate(SingleModelValidationContext context) {
        return context.getModel().getServiceTasks().stream()
                .filter(task -> task.hasImplementation() && !usesExternalTask(task))
                .map(task -> new ValidationViolation(
                        id,
                        severity,
                        task.getId(),
                        context.getModel().getProcessId(),
                        "Service task '" + task.getId()
                                + "' must be an external task (camunda:type=\"external\" with a topic)"))
                .toList();
    }

    private boolean usesExternalTask(ServiceTaskDefinition task) {
        Object rawKind = task.getEngineSpecificProperties().get(ServiceTaskDefinition.IMPL_KIND_KEY);
        String kind = rawKind instanceof String ? (String) rawKind : null;
        return EXTERNAL_TASK_KIND.equals(kind);
    }
}
