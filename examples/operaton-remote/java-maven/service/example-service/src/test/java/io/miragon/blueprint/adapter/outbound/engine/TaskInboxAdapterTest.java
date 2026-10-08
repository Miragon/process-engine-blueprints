package io.miragon.blueprint.adapter.outbound.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.outbound.TaskInboxPort.OpenClarification;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.operaton.rest.client.api.ProcessInstanceApi;
import org.operaton.rest.client.api.TaskApi;
import org.operaton.rest.client.model.ProcessInstanceDto;
import org.operaton.rest.client.model.TaskQueryDto;
import org.operaton.rest.client.model.TaskWithAttachmentAndCommentDto;

class TaskInboxAdapterTest {

    private final TaskApi taskApi = mock(TaskApi.class);
    private final ProcessInstanceApi processInstanceApi = mock(ProcessInstanceApi.class);
    private final TaskInboxAdapter underTest = new TaskInboxAdapter(taskApi, processInstanceApi);

    @Test
    void mapsOpenClarifyAlternativeTasksToTheirApplicationIdAndWaitingSince() {

        // given: one open clarify-alternative task whose instance carries the application id as its key
        UUID applicationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        OffsetDateTime created = OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC);
        when(taskApi.queryTasks(isNull(), isNull(), any(TaskQueryDto.class)))
                .thenReturn(List.of(new TaskWithAttachmentAndCommentDto()
                        .id("task-1").processInstanceId("pi-1").created(created)));
        when(processInstanceApi.getProcessInstance("pi-1"))
                .thenReturn(new ProcessInstanceDto().id("pi-1").businessKey(applicationId.toString()));

        // when: the inbox is read
        List<OpenClarification> result = underTest.findOpenClarifications();

        // then: the task is translated into the domain business key, no engine task id leaks
        assertThat(result).hasSize(1);
        assertThat(result.get(0).applicationId().value()).isEqualTo(applicationId);
    }

    @Test
    void skipsTasksWhoseProcessInstanceHasNoBusinessKey() {

        // given: an open task whose instance lost its business key (defensive)
        when(taskApi.queryTasks(isNull(), isNull(), any(TaskQueryDto.class)))
                .thenReturn(List.of(new TaskWithAttachmentAndCommentDto()
                        .id("task-1").processInstanceId("pi-1").created(OffsetDateTime.now(ZoneOffset.UTC))));
        when(processInstanceApi.getProcessInstance("pi-1"))
                .thenReturn(new ProcessInstanceDto().id("pi-1").businessKey(null));

        // when / then: it is silently dropped rather than surfacing an unusable case
        assertThat(underTest.findOpenClarifications()).isEmpty();
    }
}
