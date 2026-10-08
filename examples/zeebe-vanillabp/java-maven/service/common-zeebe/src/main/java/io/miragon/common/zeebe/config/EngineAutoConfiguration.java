package io.miragon.common.zeebe.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(ZeebeEnvironmentConfiguration.class)
public class EngineAutoConfiguration {
}
