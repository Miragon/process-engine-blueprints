package io.miragon.blueprint.adapter.outbound.zeebe;

import dev.bpmcrafters.processengineapi.task.TaskInformation;
import dev.bpmcrafters.processengineapi.task.support.UserTaskSupport;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.blueprint.application.port.outbound.TaskInboxPort;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Lists the open {@code Clarify alternative with customer} tasks from the {@link UserTaskSupport} pool and
 * translates them into the domain's business key. Zeebe has no business key, so the application id travels
 * as a process variable delivered with the task. It never leaks an engine task id upward — the inbox lists
 * cases, and cases are resolved through the domain, correlated by id.
 *
 * <p>The pool is fed by the adapter's scheduled user-task delivery, so it lags the engine by the polling
 * interval plus the secondary storage's delay. A task that left the pool in the meantime is skipped.
 */
@Component
public class TaskInboxAdapter implements TaskInboxPort {

    /** Set by the adapter's {@code SCHEDULED} delivery only, see {@code docs/adr/0014}. */
    private static final String CREATION_DATE = "creationDate";

    private static final String APPLICATION_ID_VARIABLE =
            FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.getValue();

    private final UserTaskSupport userTaskSupport;

    public TaskInboxAdapter(UserTaskSupport userTaskSupport) {
        this.userTaskSupport = userTaskSupport;
    }

    @Override
    public List<OpenClarification> findOpenClarifications() {
        List<OpenClarification> clarifications = new ArrayList<>();
        for (TaskInformation task : userTaskSupport.getAllTasks()) {
            String applicationId = readApplicationId(task);
            OffsetDateTime creationDate = task.getMetaValueAsOffsetDate(CREATION_DATE);
            if (applicationId != null && creationDate != null) {
                clarifications.add(new OpenClarification(ApplicationId.of(applicationId), creationDate.toLocalDateTime()));
            }
        }
        return clarifications;
    }

    private String readApplicationId(TaskInformation task) {
        try {
            return userTaskSupport.getPayload(task.getTaskId()).get(APPLICATION_ID_VARIABLE) instanceof String id ? id : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
