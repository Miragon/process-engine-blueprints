package io.miragon.blueprint.adapter.process

import io.temporal.workflow.SignalMethod
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod

@WorkflowInterface
interface CancelBikeOrderWorkflow {

    @WorkflowMethod
    fun run(orderId: String)

    @SignalMethod
    fun returnClarified()

    companion object {
        fun workflowId(applicationId: String) = "$applicationId-cancel-order"
    }
}
