package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.miragon.blueprint.application.port.outbound.ContractPort;
import io.miragon.blueprint.domain.leasing.ContractId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.junit.jupiter.api.Test;

class CancelContractServiceTest {

    private final ContractPort contract = mock(ContractPort.class);
    private final CancelContractService underTest = new CancelContractService(contract);

    @Test
    void cancelContractRevokesTheContractRecordedOnTheApplication() {

        // given: an application that already carries an issued contract
        LeasingApplication application = testLeasingApplication().contractId(new ContractId("CONTRACT-1")).build();

        // when: the contract is cancelled as part of the compensation
        underTest.cancelContract(application);

        // then: the recorded contract id is revoked in the contract system
        verify(contract).revokeContract(new ContractId("CONTRACT-1"));
        verifyNoMoreInteractions(contract);
    }

    @Test
    void cancelContractFailsWhenNoContractWasIssuedForTheApplication() {

        // given: an application that never received a contract
        LeasingApplication application = testLeasingApplication().contractId(null).build();

        // when/then: cancellation fails and no contract is touched
        assertThatThrownBy(() -> underTest.cancelContract(application))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(contract);
    }
}
