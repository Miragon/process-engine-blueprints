package io.miragon.blueprint.adapter.outbound.engine

import io.miragon.blueprint.application.port.outbound.LeasingProcess
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.LeasingApplication
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.Elements
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.Messages
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.Variables
import mu.KotlinLogging
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Drives the *remote* CIB seven engine over its REST API (`/engine-rest`): starts the process via its
 * message start event, correlates the messages that release the wait states, and completes the
 * alternative-clarification user task from the outside. The service-tasks are handled separately via
 * the external-task client.
 *
 * As in the embedded blueprint, the [ApplicationId] is used as the **process business key**, so
 * messages and user-task lookups correlate to the right instance by business key. Variable and element
 * names come from the typed process API generated from `bike-leasing.bpmn` in `common-package`.
 */
@Component
class RemoteLeasingProcessAdapter(
    private val engineRestClient: RestClient,
) : LeasingProcess {

    private val log = KotlinLogging.logger {}

    override fun submitRequest(application: LeasingApplication) {
        val start = Variables.StartEventLeasingRequestReceived
        engineRestClient.post()
            .uri("/message")
            .body(
                mapOf(
                    "messageName" to Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.value,
                    "businessKey" to application.id.value.toString(),
                    "processVariables" to mapOf(
                        start.APPLICATION_ID.value to typedVar(application.id.value.toString()),
                        start.BIKE_ID.value to typedVar(application.bikeId.value),
                        start.MONTHLY_NET_INCOME.value to typedVar(application.monthlyNetIncome),
                        start.AGE.value to typedVar(application.age),
                    ),
                ),
            )
            .retrieve()
            .toBodilessEntity()
        log.info { "Started remote leasing process for application ${application.id.value}" }
    }

    override fun correlateContractSigned(id: ApplicationId) =
        correlateByBusinessKey(Messages.MIRAVELO_CONTRACT_SIGNED.value, id)

    override fun correlateHandoverReported(id: ApplicationId) =
        correlateByBusinessKey(Messages.MIRAVELO_HANDOVER_REPORTED.value, id)

    override fun correlateApplicationWithdrawn(id: ApplicationId) =
        correlateByBusinessKey(Messages.MIRAVELO_APPLICATION_WITHDRAWN.value, id)

    /**
     * Completes the `Clarify alternative with customer` user task via the engine's REST API — the same
     * task a human could complete through its deployed Camunda Form in the Tasklist.
     */
    override fun completeAlternativeClarification(
        id: ApplicationId,
        alternativeFound: Boolean,
        bikeId: BikeId?,
    ) {
        val taskId = findTaskId(id, Elements.USER_TASK_CLARIFY_ALTERNATIVE.value)
        val variables = buildMap {
            put(Variables.UserTaskClarifyAlternative.ALTERNATIVE_FOUND.value, typedVar(alternativeFound))
            // The re-order reads the same start-injected bike variable, so reuse its name.
            bikeId?.let { put(Variables.StartEventLeasingRequestReceived.BIKE_ID.value, typedVar(it.value)) }
        }
        engineRestClient.post()
            .uri("/task/{id}/complete", taskId)
            .body(mapOf("variables" to variables))
            .retrieve()
            .toBodilessEntity()
        log.info { "Completed clarify-alternative task ($taskId) for application ${id.value}" }
    }

    private fun correlateByBusinessKey(messageName: String, id: ApplicationId) {
        engineRestClient.post()
            .uri("/message")
            .body(mapOf("messageName" to messageName, "businessKey" to id.value.toString()))
            .retrieve()
            .toBodilessEntity()
        log.info { "Correlated '$messageName' to application ${id.value}" }
    }

    private fun findTaskId(id: ApplicationId, taskDefinitionKey: String): String {
        val tasks = engineRestClient.get()
            .uri {
                it.path("/task")
                    .queryParam("processInstanceBusinessKey", id.value.toString())
                    .queryParam("taskDefinitionKey", taskDefinitionKey)
                    .build()
            }
            .retrieve()
            .body(object : ParameterizedTypeReference<List<TaskDto>>() {})
            ?: emptyList()
        return tasks.firstOrNull()?.id
            ?: error("No active task '$taskDefinitionKey' found for application ${id.value}")
    }

    /** Builds a CIB seven REST typed-variable payload (`{ value, type }`) from a Kotlin value. */
    private fun typedVar(value: Any?): Map<String, Any?> {
        val type = when (value) {
            is String -> "String"
            is Boolean -> "Boolean"
            is Int -> "Integer"
            is Long -> "Long"
            is Double -> "Double"
            null -> "Null"
            else -> error("Unsupported process-variable type: ${value::class}")
        }
        return mapOf("value" to value, "type" to type)
    }

    private data class TaskDto(val id: String)
}
