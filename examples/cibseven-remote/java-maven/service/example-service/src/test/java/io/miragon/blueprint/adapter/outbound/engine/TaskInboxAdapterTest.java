package io.miragon.blueprint.adapter.outbound.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.outbound.TaskInboxPort;
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.FlowNodes;
import org.cibseven.rest.client.api.ProcessInstanceApi;
import org.cibseven.rest.client.api.TaskApi;
import org.cibseven.rest.client.model.ProcessInstanceDto;
import org.cibseven.rest.client.model.TaskWithAttachmentAndCommentDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

class TaskInboxAdapterTest {

    private final TaskApi taskApi = mock(TaskApi.class);
    private final ProcessInstanceApi processInstanceApi = mock(ProcessInstanceApi.class);
    private final TaskInboxAdapter underTest = new TaskInboxAdapter(taskApi, processInstanceApi);

    @Test
    @DisplayName("maps open clarify-alternative tasks to their application id and waiting-since")
    void mapsOpenClarifyAlternativeTasksToTheirApplicationIdAndWaitingSince() {

        // given: one open clarify-alternative task whose instance carries the application id as its key
        UUID applicationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        OffsetDateTime created = OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC);
        when(taskApi.getTasks(
                new TaskApi.GetTasksRequest().taskDefinitionKey(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID)))
                .thenReturn(List.of(
                        new TaskWithAttachmentAndCommentDto().id("task-1").processInstanceId("pi-1").created(created)));
        when(processInstanceApi.getProcessInstance("pi-1"))
                .thenReturn(new ProcessInstanceDto().id("pi-1").businessKey(applicationId.toString()));

        // when: the inbox is read
        List<TaskInboxPort.OpenClarification> result = underTest.findOpenClarifications();

        // then: the task is translated into the domain business key, no engine task id leaks
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().applicationId().value()).isEqualTo(applicationId);
    }

    @Test
    @DisplayName("skips tasks whose process instance has no business key")
    void skipsTasksWhoseProcessInstanceHasNoBusinessKey() {

        // given: an open task whose instance lost its business key (defensive)
        when(taskApi.getTasks(
                new TaskApi.GetTasksRequest().taskDefinitionKey(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID)))
                .thenReturn(List.of(
                        new TaskWithAttachmentAndCommentDto()
                                .id("task-1")
                                .processInstanceId("pi-1")
                                .created(OffsetDateTime.now(ZoneOffset.UTC))));
        when(processInstanceApi.getProcessInstance("pi-1"))
                .thenReturn(new ProcessInstanceDto().id("pi-1").businessKey(null));

        // when / then: it is silently dropped rather than surfacing an unusable case
        assertThat(underTest.findOpenClarifications()).isEmpty();
    }

    @Test
    @DisplayName("skips tasks without a process instance")
    void skipsTasksWithoutAProcessInstance() {

        // given: an open task that is not bound to a process instance (defensive)
        when(taskApi.getTasks(
                new TaskApi.GetTasksRequest().taskDefinitionKey(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID)))
                .thenReturn(List.of(
                        new TaskWithAttachmentAndCommentDto().id("task-1").created(OffsetDateTime.now(ZoneOffset.UTC))));

        // when / then: it is dropped without asking the engine for an instance
        assertThat(underTest.findOpenClarifications()).isEmpty();
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    @DisplayName("skips tasks without a creation time")
    void skipsTasksWithoutACreationTime() {

        // given: an open task whose creation time is missing (defensive)
        UUID applicationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(taskApi.getTasks(
                new TaskApi.GetTasksRequest().taskDefinitionKey(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID)))
                .thenReturn(List.of(new TaskWithAttachmentAndCommentDto().id("task-1").processInstanceId("pi-1")));
        when(processInstanceApi.getProcessInstance("pi-1"))
                .thenReturn(new ProcessInstanceDto().id("pi-1").businessKey(applicationId.toString()));

        // when / then: it is dropped because the inbox cannot tell how long it has been waiting
        assertThat(underTest.findOpenClarifications()).isEmpty();
    }
}
