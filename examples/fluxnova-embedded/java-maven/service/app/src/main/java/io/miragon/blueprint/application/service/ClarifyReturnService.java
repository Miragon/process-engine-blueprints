package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.inbound.ClarifyReturnUseCase;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ClarifyReturnService implements ClarifyReturnUseCase {

    private final LeasingApplicationRepository repository;
    private final LeasingProcess process;

    public ClarifyReturnService(LeasingApplicationRepository repository, LeasingProcess process) {
        this.repository = repository;
        this.process = process;
    }

    @Override
    public void clarifyReturn(ApplicationId id) {
        repository.findById(id).orElseThrow(() -> new IllegalStateException("Unknown application " + id));
        process.completeReturnClarification(id);
    }
}
