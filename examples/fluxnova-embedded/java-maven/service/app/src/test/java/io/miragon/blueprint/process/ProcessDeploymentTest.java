package io.miragon.blueprint.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi;
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi;
import java.util.List;
import org.finos.fluxnova.bpm.engine.ProcessEngine;
import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.repository.DecisionDefinition;
import org.finos.fluxnova.bpm.engine.repository.ProcessDefinition;
import org.finos.fluxnova.bpm.engine.repository.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ProcessDeploymentTest {

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    private RepositoryService repositoryService;

    @Test
    void theEmbeddedEngineIsFluxnova() {
        assertThat(processEngine.getClass().getPackageName()).startsWith("org.finos.fluxnova.bpm.engine");
    }

    @Test
    void bothBpmnModelsAreDeployedAsProcessDefinitions() {
        List<String> deployedProcessKeys = repositoryService.createProcessDefinitionQuery().latestVersion().list()
                .stream()
                .map(ProcessDefinition::getKey)
                .toList();

        assertThat(deployedProcessKeys).containsExactlyInAnyOrder(
                BikeLeasingProcessProcessApi.PROCESS_ID.getValue(),
                CancelBikeOrderProcessApi.PROCESS_ID.getValue());
    }

    @Test
    void theCreditRatingDmnIsDeployedAsDecisionDefinition() {
        List<String> deployedDecisionKeys = repositoryService.createDecisionDefinitionQuery().latestVersion().list()
                .stream()
                .map(DecisionDefinition::getKey)
                .toList();

        assertThat(deployedDecisionKeys).containsExactly("checkCreditRating");
    }

    @Test
    void theUserTaskFormsAreDeployedWithTheModels() {
        List<String> deployedFormFiles = repositoryService.createDeploymentQuery().list()
                .stream()
                .flatMap(deployment -> repositoryService.getDeploymentResources(deployment.getId()).stream())
                .map(Resource::getName)
                .map(name -> name.substring(name.lastIndexOf('/') + 1))
                .filter(name -> name.endsWith(".form"))
                .toList();

        assertThat(deployedFormFiles).containsExactlyInAnyOrder("clarify-alternative.form", "clarify-return.form");
    }
}
