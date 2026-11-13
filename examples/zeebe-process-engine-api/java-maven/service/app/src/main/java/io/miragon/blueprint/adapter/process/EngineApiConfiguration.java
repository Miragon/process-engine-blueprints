package io.miragon.blueprint.adapter.process;

import dev.bpmcrafters.processengineapi.CommonRestrictions;
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi;
import dev.bpmcrafters.processengineapi.task.support.UserTaskSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the process-engine-api into the service.
 */
@Configuration
public class EngineApiConfiguration {

    /**
     * In-memory pool that collects the {@code clarifyAlternative} and {@code clarifyReturn} user tasks delivered
     * by the adapter, so
     * the outbound adapters can resolve a task's id, complete it via the UserTaskCompletionApi and list
     * the open ones for the inbox.
     */
    @Bean
    public UserTaskSupport userTaskSupport(TaskSubscriptionApi taskSubscriptionApi) {
        UserTaskSupport userTaskSupport = new UserTaskSupport();
        userTaskSupport.subscribe(
                taskSubscriptionApi,
                CommonRestrictions.builder().build(),
                BikeLeasingProcessProcessApi.FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID,
                null);
        userTaskSupport.subscribe(
                taskSubscriptionApi,
                CommonRestrictions.builder().build(),
                CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID,
                null);
        return userTaskSupport;
    }
}
