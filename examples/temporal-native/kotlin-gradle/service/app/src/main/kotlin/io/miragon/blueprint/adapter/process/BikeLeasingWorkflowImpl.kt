package io.miragon.blueprint.adapter.process

import io.temporal.activity.ActivityCancellationType
import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.failure.ActivityFailure
import io.temporal.failure.ApplicationFailure
import io.temporal.failure.TemporalFailure
import io.temporal.spring.boot.WorkflowImpl
import io.temporal.workflow.Async
import io.temporal.workflow.ChildWorkflowOptions
import io.temporal.workflow.Functions
import io.temporal.workflow.Promise
import io.temporal.workflow.Saga
import io.temporal.workflow.Workflow
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * The leasing flow of `bike-leasing.bpmn` as code. The main flow runs in one cancellation scope that
 * [applicationWithdrawn] cancels, the counterpart of the interrupting event sub-process. A compensation
 * joins the [saga] only once its step completed, as in BPMN compensation.
 */
@WorkflowImpl(taskQueues = [BIKE_LEASING_TASK_QUEUE])
class BikeLeasingWorkflowImpl : BikeLeasingWorkflow {

    private val activityOptions = ActivityOptions.newBuilder().setStartToCloseTimeout(ACTIVITY_TIMEOUT).build()

    /** Waits for a running step on withdrawal: its side effect must end up in the saga, not get lost. */
    private val compensableActivityOptions =
        ActivityOptions.newBuilder(activityOptions)
            .setCancellationType(ActivityCancellationType.WAIT_CANCELLATION_COMPLETED)
            .build()

    private val dealerActivityOptions =
        ActivityOptions.newBuilder(compensableActivityOptions)
            .setRetryOptions(
                RetryOptions.newBuilder()
                    .setInitialInterval(DEALER_RETRY_INTERVAL)
                    .setBackoffCoefficient(1.0)
                    .setDoNotRetry(BIKE_UNAVAILABLE_FAILURE)
                    .build(),
            )
            .build()

    private val sendContract = Workflow.newActivityStub(SendContract::class.java, activityOptions)
    private val sendReminderMail = Workflow.newActivityStub(SendReminderMail::class.java, activityOptions)
    private val sendRejection = Workflow.newActivityStub(SendRejection::class.java, activityOptions)
    private val orderBike = Workflow.newActivityStub(OrderBike::class.java, dealerActivityOptions)
    private val issueInsurancePolicy = Workflow.newActivityStub(IssueInsurancePolicy::class.java, compensableActivityOptions)
    private val activateLeasing = Workflow.newActivityStub(ActivateLeasing::class.java, activityOptions)
    private val cancelContract = Workflow.newActivityStub(CancelContract::class.java, activityOptions)
    private val cancelPolicy = Workflow.newActivityStub(CancelPolicy::class.java, activityOptions)
    private val sendCancellationConfirmation =
        Workflow.newActivityStub(SendCancellationConfirmation::class.java, activityOptions)

    private val saga = Saga(Saga.Options.Builder().build())

    private var contractSigned = false
    private var handoverReported = false
    private var applicationWithdrawn = false
    private var clarificationOpen = false
    private var alternativeDecision: AlternativeDecision? = null

    override fun run(request: LeasingRequest) {
        var leasingReversed = false
        val leasing = Workflow.newCancellationScope(Runnable { leasingReversed = !lease(request) })
        Async.procedure(
            Functions.Proc {
                Workflow.await { applicationWithdrawn }
                leasing.cancel()
            },
        )
        try {
            leasing.run()
        } catch (failure: TemporalFailure) {
            if (!leasing.isCancelRequested) throw failure
        }
        if (leasing.isCancelRequested || leasingReversed) {
            saga.compensate()
            sendCancellationConfirmation.sendCancellationConfirmation(request.applicationId)
        }
    }

    override fun contractSigned() {
        contractSigned = true
    }

    override fun handoverReported() {
        handoverReported = true
    }

    override fun applicationWithdrawn() {
        applicationWithdrawn = true
    }

    override fun clarifyAlternative(decision: AlternativeDecision) {
        clarificationOpen = false
        alternativeDecision = decision
    }

    override fun rejectClarificationWithoutOpenTask(decision: AlternativeDecision) {
        check(clarificationOpen) { "No open clarification" }
    }

    private fun lease(request: LeasingRequest): Boolean {
        val applicationId = request.applicationId
        if (!isSolvent(request.age, request.monthlyNetIncome) || !concludeContract(applicationId)) {
            sendRejection.sendRejection(applicationId)
            return true
        }
        if (!orderBikeAndIssuePolicy(request)) return false
        Workflow.await { handoverReported }
        Workflow.sleep(WITHDRAWAL_PERIOD)
        activateLeasing.activateLeasing(applicationId)
        return true
    }

    private fun concludeContract(applicationId: String): Boolean {
        val reminder = Workflow.newCancellationScope(
            Runnable {
                Async.procedure(
                    Functions.Proc {
                        Workflow.sleep(REMINDER_DELAY)
                        sendReminderMail.sendReminderMail(applicationId)
                    },
                )
            },
        )
        reminder.run()
        sendContract.sendContract(applicationId)
        val signed = Workflow.await(SIGNATURE_DEADLINE) { contractSigned }
        reminder.cancel()
        if (signed) saga.addCompensation(Functions.Proc { cancelContract.cancelContract(applicationId) })
        return signed
    }

    private fun orderBikeAndIssuePolicy(request: LeasingRequest): Boolean {
        val policyIssued = Async.procedure(
            Functions.Proc {
                issueInsurancePolicy.issueInsurancePolicy(request.applicationId)
                saga.addCompensation(Functions.Proc { cancelPolicy.cancelPolicy(request.applicationId) })
            },
        )
        try {
            return orderBikeOrAlternative(request)
        } finally {
            awaitSettled(policyIssued)
        }
    }

    private fun awaitSettled(step: Promise<Void>) {
        try {
            step.get()
        } catch (_: TemporalFailure) {
            return
        }
    }

    private fun orderBikeOrAlternative(request: LeasingRequest): Boolean {
        var bikeId = request.bikeId
        while (true) {
            try {
                val orderId = orderBike.orderBike(request.applicationId, bikeId)
                saga.addCompensation(Functions.Proc { cancelBikeOrder(request.applicationId, orderId) })
                return true
            } catch (failure: ActivityFailure) {
                if (!failure.isBikeUnavailable()) throw failure
            }
            val decision = clarifyWithCustomer()
            if (!decision.alternativeFound) return false
            bikeId = decision.bikeId ?: bikeId
        }
    }

    private fun clarifyWithCustomer(): AlternativeDecision {
        alternativeDecision = null
        clarificationOpen = true
        val waitingSince = Instant.ofEpochMilli(Workflow.currentTimeMillis()).atOffset(ZoneOffset.UTC)
        Workflow.upsertTypedSearchAttributes(CLARIFICATION_WAITING_SINCE.valueSet(waitingSince))
        try {
            Workflow.await { alternativeDecision != null }
            return checkNotNull(alternativeDecision)
        } finally {
            clarificationOpen = false
            Workflow.upsertTypedSearchAttributes(CLARIFICATION_WAITING_SINCE.valueUnset())
        }
    }

    private fun cancelBikeOrder(applicationId: String, orderId: String) {
        val options = ChildWorkflowOptions.newBuilder()
            .setWorkflowId(CancelBikeOrderWorkflow.workflowId(applicationId))
            .build()
        Workflow.newChildWorkflowStub(CancelBikeOrderWorkflow::class.java, options).run(orderId)
    }

    private fun ActivityFailure.isBikeUnavailable() = (cause as? ApplicationFailure)?.type == BIKE_UNAVAILABLE_FAILURE

    private companion object {
        val ACTIVITY_TIMEOUT: Duration = Duration.ofSeconds(30)
        val DEALER_RETRY_INTERVAL: Duration = Duration.ofSeconds(10)
        val REMINDER_DELAY: Duration = Duration.ofDays(7)
        val SIGNATURE_DEADLINE: Duration = Duration.ofDays(14)
        val WITHDRAWAL_PERIOD: Duration = Duration.ofDays(14)
    }
}
