package io.miragon.blueprint.adapter.outbound.engine;

import io.miragon.blueprint.application.port.outbound.TaskInboxPort;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.FlowNodes;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.operaton.rest.client.api.ProcessInstanceApi;
import org.operaton.rest.client.api.TaskApi;
import org.operaton.rest.client.model.TaskQueryDto;
import org.operaton.rest.client.model.TaskWithAttachmentAndCommentDto;
import org.springframework.stereotype.Component;

/**
 * Reads the open {@code Clarify alternative with customer} tasks from the <em>remote</em> engine over
 * its REST API (via the generated {@link TaskApi}) and translates them into the domain's business key
 * (the application id). It never leaks an engine task id upward: the inbox lists cases, and cases are
 * resolved through the domain, correlated by id.
 *
 * <p>The engine's task list does not carry the business key on the task itself, so each task's process
 * instance is resolved through {@link ProcessInstanceApi} to recover the application id — the remote
 * counterpart to the embedded blueprint reading it straight from {@code RuntimeService}.
 */
@Component
public class TaskInboxAdapter implements TaskInboxPort {

    private final TaskApi taskApi;
    private final ProcessInstanceApi processInstanceApi;

    public TaskInboxAdapter(TaskApi taskApi, ProcessInstanceApi processInstanceApi) {
        this.taskApi = taskApi;
        this.processInstanceApi = processInstanceApi;
    }

    @Override
    public List<OpenClarification> findOpenClarifications() {
        List<TaskWithAttachmentAndCommentDto> tasks = taskApi.queryTasks(null, null,
                new TaskQueryDto().taskDefinitionKey(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID));
        List<OpenClarification> clarifications = new ArrayList<>();
        for (TaskWithAttachmentAndCommentDto task : tasks) {
            String processInstanceId = task.getProcessInstanceId();
            if (processInstanceId == null) {
                continue;
            }
            String businessKey = processInstanceApi.getProcessInstance(processInstanceId).getBusinessKey();
            if (businessKey == null) {
                continue;
            }
            OffsetDateTime created = task.getCreated();
            if (created == null) {
                continue;
            }
            clarifications.add(new OpenClarification(
                    ApplicationId.of(businessKey),
                    created.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()));
        }
        return clarifications;
    }
}
