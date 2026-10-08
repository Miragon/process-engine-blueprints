package io.miragon.blueprint.adapter.inbound.workflow;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.TaskException;
import io.vanillabp.spi.service.WorkflowService;
import org.springframework.transaction.annotation.Transactional;

@WorkflowService(
        workflowAggregateClass = LeasingApplicationAggregate.class,
        bpmnProcess = @BpmnProcess(bpmnProcessId = "bikeLeasingProcess"),
        secondaryBpmnProcesses = @BpmnProcess(bpmnProcessId = "cancelBikeOrder"))
@Transactional(noRollbackFor = TaskException.class)
public abstract class BikeLeasingWorkflow {
}
