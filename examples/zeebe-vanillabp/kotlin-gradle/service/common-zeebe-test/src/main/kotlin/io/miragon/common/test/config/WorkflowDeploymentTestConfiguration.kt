package io.miragon.common.test.config

import io.camunda.client.event.CamundaClientCreatedEvent
import io.vanillabp.camunda8.deployment.Camunda8DeploymentAdapter
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.event.EventListener

/**
 * VanillaBP's Camunda 8 adapter deploys the BPMN models and opens its job workers once, on Spring
 * Boot's `ApplicationReadyEvent`. `@CamundaSpringProcessTest` hands out a fresh client per test, long
 * after that event, so this configuration repeats the deployment for every created client.
 */
@TestConfiguration
class WorkflowDeploymentTestConfiguration(
    private val deployment: Camunda8DeploymentAdapter,
) {

    @EventListener(CamundaClientCreatedEvent::class)
    fun deployWorkflowsForTestClient() {
        deployment.deployBpmnModels()
    }
}
