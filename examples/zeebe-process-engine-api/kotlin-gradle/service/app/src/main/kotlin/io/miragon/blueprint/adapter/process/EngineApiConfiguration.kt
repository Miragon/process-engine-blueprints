package io.miragon.blueprint.adapter.process

import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import dev.bpmcrafters.processengineapi.task.support.UserTaskSupport
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the process-engine-api into the service.
 */
@Configuration
class EngineApiConfiguration {

    /**
     * In-memory pool that collects the `clarifyAlternative` user tasks delivered by the adapter, so
     * the outbound adapters can resolve a task's id, complete it via the UserTaskCompletionApi and list
     * the open ones for the inbox.
     */
    @Bean
    fun clarifyAlternativeUserTaskSupport(taskSubscriptionApi: TaskSubscriptionApi): UserTaskSupport =
        UserTaskSupport().apply {
            subscribe(
                taskSubscriptionApi = taskSubscriptionApi,
                taskDescriptionKey = FlowNodes.UserTaskClarifyAlternative.id.value,
            )
        }
}
