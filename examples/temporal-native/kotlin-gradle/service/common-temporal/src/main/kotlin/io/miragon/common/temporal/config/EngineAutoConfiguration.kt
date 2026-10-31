package io.miragon.common.temporal.config

import io.temporal.common.converter.DataConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(TemporalEnvironmentConfiguration::class)
class EngineAutoConfiguration {

    @Bean
    fun dataConverter(): DataConverter = kotlinDataConverter()
}
