package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.vanillabp.spi.service.TaskException
import io.vanillabp.spi.service.WorkflowService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource

class BikeLeasingWorkflowTest {

    private val task = SendContractTask::class.java
    private val taskMethod = task.getMethod("sendContract", LeasingApplicationAggregate::class.java)

    @Test
    fun `every workflow task inherits the binding to the leasing process and its called process`() {
        // when
        val workflowService = task.getAnnotation(WorkflowService::class.java)

        // then
        assertThat(workflowService.workflowAggregateClass.java).isEqualTo(LeasingApplicationAggregate::class.java)
        assertThat(workflowService.bpmnProcess.bpmnProcessId)
            .isEqualTo(BikeLeasingProcessProcessApi.PROCESS_ID.value)
        assertThat(workflowService.secondaryBpmnProcesses.map { it.bpmnProcessId })
            .containsExactly(CancelBikeOrderProcessApi.PROCESS_ID.value)
    }

    @Test
    fun `every workflow task runs in a transaction that keeps the aggregate changes when it raises a BPMN error`() {
        // when
        val transaction = AnnotationTransactionAttributeSource().getTransactionAttribute(taskMethod, task)

        // then
        assertThat(transaction).isNotNull()
        assertThat(transaction?.rollbackOn(TaskException("applicationInvalid"))).isFalse()
        assertThat(transaction?.rollbackOn(IllegalStateException("dealer outage"))).isTrue()
    }
}
