package io.miragon.blueprint.adapter.outbound.workflow;

import io.miragon.blueprint.adapter.outbound.db.LeasingApplicationJpaRepository;
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.adapter.process.Messages;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.bpmn.runtime.MessageName;
import io.vanillabp.spi.process.ProcessService;
import org.springframework.stereotype.Component;

@Component
public class LeasingProcessAdapter implements LeasingProcess {

    private final ProcessService<LeasingApplicationAggregate> processService;
    private final LeasingApplicationJpaRepository aggregates;

    public LeasingProcessAdapter(
            ProcessService<LeasingApplicationAggregate> processService,
            LeasingApplicationJpaRepository aggregates) {
        this.processService = processService;
        this.aggregates = aggregates;
    }

    @Override
    public void submitRequest(LeasingApplication application) {
        processService.startWorkflowByMessage(
                LeasingApplicationAggregate.of(application),
                Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.getValue());
    }

    @Override
    public void correlateContractSigned(ApplicationId id) {
        correlate(id, Messages.MIRAVELO_CONTRACT_SIGNED);
    }

    @Override
    public void correlateHandoverReported(ApplicationId id) {
        correlate(id, Messages.MIRAVELO_HANDOVER_REPORTED);
    }

    @Override
    public void correlateApplicationWithdrawn(ApplicationId id) {
        correlate(id, Messages.MIRAVELO_APPLICATION_WITHDRAWN);
    }

    @Override
    public void completeAlternativeClarification(ApplicationId id, boolean alternativeFound) {
        LeasingApplicationAggregate aggregate = aggregateOf(id);
        String taskId = aggregate.resolveClarification(alternativeFound);
        processService.completeUserTask(aggregate, taskId);
    }

    private void correlate(ApplicationId id, MessageName message) {
        processService.correlateMessage(aggregateOf(id), message.getValue());
    }

    private LeasingApplicationAggregate aggregateOf(ApplicationId id) {
        return aggregates.findById(id.value().toString())
                .orElseThrow(() -> new IllegalStateException("Unknown application " + id.value()));
    }
}
