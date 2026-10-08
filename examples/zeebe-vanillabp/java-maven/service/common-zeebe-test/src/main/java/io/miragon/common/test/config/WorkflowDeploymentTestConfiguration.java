package io.miragon.common.test.config;

import io.camunda.client.event.CamundaClientCreatedEvent;
import io.vanillabp.camunda8.deployment.Camunda8DeploymentAdapter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.event.EventListener;

/**
 * VanillaBP's Camunda 8 adapter deploys the BPMN models and opens its job workers once, on Spring
 * Boot's {@code ApplicationReadyEvent}. {@code @CamundaSpringProcessTest} hands out a fresh client per
 * test, long after that event, so this configuration repeats the deployment for every created client.
 */
@TestConfiguration
public class WorkflowDeploymentTestConfiguration {

    private final Camunda8DeploymentAdapter deployment;

    public WorkflowDeploymentTestConfiguration(Camunda8DeploymentAdapter deployment) {
        this.deployment = deployment;
    }

    @EventListener(CamundaClientCreatedEvent.class)
    public void deployWorkflowsForTestClient() {
        deployment.deployBpmnModels();
    }
}
