package io.miragon.common.temporal.config

import org.springframework.context.annotation.PropertySource
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.env.PropertiesPropertySource
import org.springframework.core.env.PropertySource as EnvPropertySource
import org.springframework.core.io.support.EncodedResource
import org.springframework.core.io.support.PropertySourceFactory
import java.util.*

@PropertySource(
    value = ["classpath:temporal-application.yaml"],
    factory = TemporalEnvironmentConfiguration.YamlPropertySourceFactory::class
)
class TemporalEnvironmentConfiguration {
    class YamlPropertySourceFactory : PropertySourceFactory {
        override fun createPropertySource(name: String?, resource: EncodedResource): EnvPropertySource<*> {
            val factory = YamlPropertiesFactoryBean()
            factory.setResources(resource.resource)
            val properties: Properties = factory.getObject() ?: Properties()
            return PropertiesPropertySource(name ?: resource.resource.filename!!, properties)
        }
    }
}
