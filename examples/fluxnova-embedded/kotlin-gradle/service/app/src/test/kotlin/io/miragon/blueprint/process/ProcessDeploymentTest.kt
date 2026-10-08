package io.miragon.blueprint.process

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi
import org.assertj.core.api.Assertions.assertThat
import org.finos.fluxnova.bpm.engine.ProcessEngine
import org.finos.fluxnova.bpm.engine.RepositoryService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class ProcessDeploymentTest {

    @Autowired
    private lateinit var processEngine: ProcessEngine

    @Autowired
    private lateinit var repositoryService: RepositoryService

    @Test
    fun `the embedded engine is Fluxnova`() {
        assertThat(processEngine.javaClass.packageName).startsWith("org.finos.fluxnova.bpm.engine")
    }

    @Test
    fun `both bpmn models are deployed as process definitions`() {
        val deployedProcessKeys =
            repositoryService.createProcessDefinitionQuery().latestVersion().list().map { it.key }

        assertThat(deployedProcessKeys).containsExactlyInAnyOrder(
            BikeLeasingProcessProcessApi.PROCESS_ID.value,
            CancelBikeOrderProcessApi.PROCESS_ID.value,
        )
    }

    @Test
    fun `the credit rating dmn is deployed as decision definition`() {
        val deployedDecisionKeys =
            repositoryService.createDecisionDefinitionQuery().latestVersion().list().map { it.key }

        assertThat(deployedDecisionKeys).containsExactly("checkCreditRating")
    }

    @Test
    fun `the user task forms are deployed with the models`() {
        val deployedFormFiles =
            repositoryService
                .createDeploymentQuery()
                .list()
                .flatMap { repositoryService.getDeploymentResources(it.id) }
                .map { it.name.substringAfterLast('/') }
                .filter { it.endsWith(".form") }

        assertThat(deployedFormFiles).containsExactlyInAnyOrder("clarify-alternative.form", "clarify-return.form")
    }
}
