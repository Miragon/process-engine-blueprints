package io.miragon.blueprint.adapter.inbound.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi;
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi;
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.TaskException;
import io.vanillabp.spi.service.WorkflowService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.util.ClassUtils;

class BikeLeasingWorkflowTest {

    private final Class<SendContractTask> task = SendContractTask.class;
    private final Method taskMethod = ClassUtils.getMethod(task, "sendContract", LeasingApplicationAggregate.class);

    @Test
    void everyWorkflowTaskInheritsTheBindingToTheLeasingProcessAndItsCalledProcess() {
        // when
        WorkflowService workflowService = task.getAnnotation(WorkflowService.class);

        // then
        assertThat(workflowService.workflowAggregateClass()).isEqualTo(LeasingApplicationAggregate.class);
        assertThat(workflowService.bpmnProcess().bpmnProcessId())
                .isEqualTo(BikeLeasingProcessProcessApi.PROCESS_ID.getValue());
        assertThat(workflowService.secondaryBpmnProcesses())
                .extracting(BpmnProcess::bpmnProcessId)
                .containsExactly(CancelBikeOrderProcessApi.PROCESS_ID.getValue());
    }

    @Test
    void everyWorkflowTaskRunsInATransactionThatKeepsTheAggregateChangesWhenItRaisesABpmnError() {
        // when
        TransactionAttribute transaction =
                new AnnotationTransactionAttributeSource().getTransactionAttribute(taskMethod, task);

        // then
        assertThat(transaction).isNotNull();
        assertThat(transaction.rollbackOn(new TaskException("bikeUnavailable"))).isFalse();
        assertThat(transaction.rollbackOn(new IllegalStateException("dealer outage"))).isTrue();
    }
}
