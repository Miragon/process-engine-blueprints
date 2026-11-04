package io.miragon.blueprint.adapter.inbound.zeebe;

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred;
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.Variable;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.blueprint.adapter.process.Errors;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.BikeUnavailableException;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Orders the bike from the dealer and returns the placed order as the process variable {@code orderId}. An
 * out-of-stock bike is no failure: it is reported as the BPMN error {@code bikeUnavailable}, which the error
 * boundary event on the service task catches, and leaving the task this way registers no order
 * compensation. Any other exception reaches the worker library, which fails the job through the adapter:
 * {@code service-tasks.retries} and {@code retry-timeout-in-seconds} in {@code application.yaml} give the
 * retry countdown with a 10s backoff, and at 0 retries Zeebe raises an incident on
 * {@code serviceTask_orderBike}. It powers the reproducible incident demo (bike id {@code BIKE-FAIL}, see the
 * {@code 06-incident-demo} Bruno collection); the poison-id logic itself lives in the simulated dealer, which
 * throws.
 */
@Component
public class OrderBikeWorker {

    private final OrderBikeUseCase useCase;

    public OrderBikeWorker(OrderBikeUseCase useCase) {
        this.useCase = useCase;
    }

    @ProcessEngineWorker(topic = ServiceTasks.MIRAVELO_ORDER_BIKE)
    public Map<String, Object> handle(@Variable String applicationId, @Variable String bikeId)
            throws BpmnErrorOccurred {
        try {
            OrderId orderId = useCase.orderBike(ApplicationId.of(applicationId), new BikeId(bikeId));
            return Map.of(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.getValue(), orderId.value());
        } catch (BikeUnavailableException e) {
            throw new BpmnErrorOccurred(e.getMessage(), Errors.BIKE_UNAVAILABLE.getCode(), Map.of());
        }
    }
}
