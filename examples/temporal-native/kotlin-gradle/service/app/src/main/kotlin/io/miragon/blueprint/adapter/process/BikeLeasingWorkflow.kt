package io.miragon.blueprint.adapter.process

import io.temporal.workflow.SignalMethod
import io.temporal.workflow.UpdateMethod
import io.temporal.workflow.UpdateValidatorMethod
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod

@WorkflowInterface
interface BikeLeasingWorkflow {

    @WorkflowMethod
    fun run(request: LeasingRequest)

    @SignalMethod
    fun contractSigned()

    @SignalMethod
    fun handoverReported()

    @SignalMethod
    fun applicationWithdrawn()

    @UpdateMethod
    fun clarifyAlternative(decision: AlternativeDecision)

    @UpdateValidatorMethod(updateName = "clarifyAlternative")
    fun rejectClarificationWithoutOpenTask(decision: AlternativeDecision)
}
