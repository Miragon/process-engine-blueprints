package io.miragon.blueprint.adapter.outbound.zeebe

import dev.bpmcrafters.processengineapi.correlation.CorrelateMessageCmd
import dev.bpmcrafters.processengineapi.correlation.Correlation
import dev.bpmcrafters.processengineapi.correlation.CorrelationApi
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.process.StartProcessByMessageCmd
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.support.UserTaskSupport
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.blueprint.adapter.process.Messages
import io.miragon.blueprint.application.port.outbound.LeasingProcess
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.LeasingApplication
import org.springframework.stereotype.Component

/**
 * Drives the Camunda 8 / Zeebe engine through the process-engine-api. Zeebe has no business key, so the
 * application id travels as a process variable and is declared as the message subscription correlation
 * key on every catch event; messages are published with that id as their correlation key. The variable
 * and element names come from the typed process API generated from `bike-leasing.bpmn`.
 *
 * The `clarifyAlternative` user task is looked up in the [UserTaskSupport] pool (fed by the adapter's
 * user-task delivery) and then completed via the [UserTaskCompletionApi] — the process-engine-api
 * has no server-side task query, so completion always goes through a resolved task id.
 */
@Component
class LeasingProcessAdapter(
    private val startProcessApi: StartProcessApi,
    private val correlationApi: CorrelationApi,
    private val userTaskSupport: UserTaskSupport,
    private val userTaskCompletionApi: UserTaskCompletionApi,
) : LeasingProcess {

    override fun submitRequest(application: LeasingApplication) {
        val start = FlowNodes.StartEventLeasingRequestReceived.Variables
        // Publishing the leasing-request message starts a new instance via the message start event.
        startProcessApi.startProcess(
            cmd = StartProcessByMessageCmd(
                messageName = Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.value,
                payload = mapOf(
                    start.APPLICATION_ID.value to application.id.value.toString(),
                    start.BIKE_ID.value to application.bikeId.value,
                    start.MONTHLY_NET_INCOME.value to application.monthlyNetIncome,
                    start.AGE.value to application.age,
                ),
            ),
        ).join()
    }

    override fun correlateContractSigned(id: ApplicationId) =
        correlate(Messages.MIRAVELO_CONTRACT_SIGNED.value, id)

    override fun correlateHandoverReported(id: ApplicationId) =
        correlate(Messages.MIRAVELO_HANDOVER_REPORTED.value, id)

    override fun correlateApplicationWithdrawn(id: ApplicationId) =
        correlate(Messages.MIRAVELO_APPLICATION_WITHDRAWN.value, id)

    private fun correlate(messageName: String, id: ApplicationId) {
        correlationApi.correlateMessage(
            cmd = CorrelateMessageCmd(
                messageName = messageName,
                payload = emptyMap(),
                correlation = Correlation.withKey(id.value.toString()),
            ),
        ).join()
    }

    /**
     * Completes the `Clarify alternative with customer` user task via the process-engine-api — the same
     * task a human could complete through its deployed Camunda Form in the Tasklist.
     */
    override fun completeAlternativeClarification(
        id: ApplicationId,
        alternativeFound: Boolean,
        bikeId: BikeId?,
    ) {
        val taskId = awaitClarifyAlternativeTaskId(id)
        val variables = buildMap<String, Any?> {
            put(FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.value, alternativeFound)
            bikeId?.let { put(FlowNodes.UserTaskClarifyAlternative.Variables.BIKE_ID.value, it.value) }
        }
        userTaskCompletionApi.completeTask(CompleteTaskCmd(taskId, variables)).join()
    }

    /**
     * Resolves the open `clarifyAlternative` task id for [id] from the [UserTaskSupport] pool, waiting
     * briefly for the adapter's user-task delivery to hand it over. Fails with
     * [NoSuchElementException], which the REST advice maps to a 409: the token is not at this task.
     */
    private fun awaitClarifyAlternativeTaskId(id: ApplicationId): String {
        val applicationId = id.value.toString()
        val applicationIdKey = FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.value
        repeat(TASK_LOOKUP_ATTEMPTS) {
            userTaskSupport.getAllTasks()
                .firstOrNull { task ->
                    runCatching { userTaskSupport.getPayload(task.taskId)[applicationIdKey] == applicationId }
                        .getOrDefault(false)
                }
                ?.let { return it.taskId }
            Thread.sleep(TASK_LOOKUP_INTERVAL_MS)
        }
        throw NoSuchElementException(
            "No active '${FlowNodes.UserTaskClarifyAlternative.id.value}' task for application $applicationId",
        )
    }

    private companion object {
        const val TASK_LOOKUP_ATTEMPTS = 50
        const val TASK_LOOKUP_INTERVAL_MS = 200L
    }
}
