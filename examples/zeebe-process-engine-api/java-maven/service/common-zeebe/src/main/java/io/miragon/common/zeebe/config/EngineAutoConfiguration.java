package io.miragon.common.zeebe.config;

import io.camunda.client.annotation.Deployment;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Deployment(
    resources = {
        "classpath:bpmn/*.bpmn",
        "classpath:dmn/*.dmn",
    }
)
@Import(ZeebeEnvironmentConfiguration.class)
public class EngineAutoConfiguration {
}
