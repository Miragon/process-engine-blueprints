package io.miragon.blueprint.adapter.process

import org.cibseven.community.process_test_coverage.spring_test.common.ProcessEngineCoverageProperties
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ContextConfigurationAttributes
import org.springframework.test.context.ContextCustomizer
import org.springframework.test.context.ContextCustomizerFactory
import org.springframework.test.context.MergedContextConfiguration

class ProcessTestCoverageContextCustomizerFactory : ContextCustomizerFactory {

    override fun createContextCustomizer(
        testClass: Class<*>,
        configAttributes: List<ContextConfigurationAttributes>,
    ): ContextCustomizer? = if (testClass.simpleName.endsWith(GATED_TEST_CLASS_SUFFIX)) MinimumCoverageGate else null

    private object MinimumCoverageGate : ContextCustomizer {
        override fun customizeContext(context: ConfigurableApplicationContext, mergedConfig: MergedContextConfiguration) {
            context.beanFactory.registerSingleton(
                "processEngineCoverageProperties",
                ProcessEngineCoverageProperties.builder().build(),
            )
        }
    }

    companion object {
        private const val GATED_TEST_CLASS_SUFFIX = "ProcessTest"
    }
}
