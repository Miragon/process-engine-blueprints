package io.miragon.blueprint.adapter.process;

import java.util.List;
import org.cibseven.community.process_test_coverage.spring_test.common.ProcessEngineCoverageProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

public class ProcessTestCoverageContextCustomizerFactory implements ContextCustomizerFactory {

    private static final String GATED_TEST_CLASS_SUFFIX = "ProcessTest";

    private static final ContextCustomizer MINIMUM_COVERAGE_GATE = new MinimumCoverageGate();

    @Override
    public ContextCustomizer createContextCustomizer(
            Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        return testClass.getSimpleName().endsWith(GATED_TEST_CLASS_SUFFIX) ? MINIMUM_COVERAGE_GATE : null;
    }

    private static final class MinimumCoverageGate implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
            context.getBeanFactory().registerSingleton(
                    "processEngineCoverageProperties",
                    ProcessEngineCoverageProperties.builder().build());
        }
    }
}
