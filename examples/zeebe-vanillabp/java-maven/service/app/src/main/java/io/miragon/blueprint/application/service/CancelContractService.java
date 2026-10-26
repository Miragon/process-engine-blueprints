package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.inbound.CancelContractUseCase;
import io.miragon.blueprint.application.port.outbound.ContractPort;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.springframework.stereotype.Service;

@Service
public class CancelContractService implements CancelContractUseCase {

    private final ContractPort contract;

    public CancelContractService(ContractPort contract) {
        this.contract = contract;
    }

    @Override
    public void cancelContract(LeasingApplication application) {
        if (application.contractId() == null) {
            throw new IllegalStateException("No contract issued for application " + application.id());
        }
        contract.revokeContract(application.contractId());
    }
}
