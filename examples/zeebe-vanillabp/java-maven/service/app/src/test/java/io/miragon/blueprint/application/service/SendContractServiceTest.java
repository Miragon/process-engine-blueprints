package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.outbound.ContractPort;
import io.miragon.blueprint.application.port.outbound.NotificationPort;
import io.miragon.blueprint.domain.leasing.ContractId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.junit.jupiter.api.Test;

class SendContractServiceTest {

    private final ContractPort contract = mock(ContractPort.class);
    private final NotificationPort notification = mock(NotificationPort.class);
    private final SendContractService underTest = new SendContractService(contract, notification);

    @Test
    void sendContractIssuesTheContractRecordsItsIdOnTheApplicationAndNotifiesTheCustomer() {

        // given: an application whose contract the contract system will issue
        LeasingApplication application = testLeasingApplication().build();
        when(contract.issueContract(application.id())).thenReturn(new ContractId("CONTRACT-1"));

        // when: the contract is sent
        LeasingApplication contracted = underTest.sendContract(application);

        // then: the contract is issued, its id is recorded on the application and the customer is asked to sign
        assertThat(contracted)
                .isEqualTo(testLeasingApplication().contractId(new ContractId("CONTRACT-1")).build());
        verify(contract).issueContract(application.id());
        verify(notification).send("Please review and sign your leasing contract", application);
        verifyNoMoreInteractions(contract, notification);
    }

    @Test
    void sendContractNeitherIssuesNorAnnouncesASecondContractWhenTheTaskIsDeliveredAgain() {

        // given: an application that already carries the contract of an earlier delivery
        LeasingApplication application = testLeasingApplication().contractId(new ContractId("CONTRACT-1")).build();

        // when: the contract is sent again
        LeasingApplication contracted = underTest.sendContract(application);

        // then: the application is untouched and no outbound system is called
        assertThat(contracted).isEqualTo(application);
        verifyNoInteractions(contract, notification);
    }
}
