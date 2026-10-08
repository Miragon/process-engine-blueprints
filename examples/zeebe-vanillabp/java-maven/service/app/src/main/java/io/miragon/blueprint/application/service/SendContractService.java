package io.miragon.blueprint.application.service;

import io.miragon.blueprint.application.port.inbound.SendContractUseCase;
import io.miragon.blueprint.application.port.outbound.ContractPort;
import io.miragon.blueprint.application.port.outbound.NotificationPort;
import io.miragon.blueprint.domain.leasing.ContractId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.springframework.stereotype.Service;

@Service
public class SendContractService implements SendContractUseCase {

    private final ContractPort contract;
    private final NotificationPort notification;

    public SendContractService(
            ContractPort contract,
            NotificationPort notification) {
        this.contract = contract;
        this.notification = notification;
    }

    @Override
    public LeasingApplication sendContract(LeasingApplication application) {
        if (application.contractId() != null) {
            return application;
        }
        ContractId contractId = contract.issueContract(application.id());
        notification.send("Please review and sign your leasing contract", application);
        return application.withContract(contractId);
    }
}
