package io.miragon.blueprint.application.port.inbound;

import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;

public interface GetLeasingApplicationQuery {
    Result byId(ApplicationId id);

    /** The application together with the model of its bike, resolved from the portfolio. */
    record Result(
            LeasingApplication application,
            String bikeModel) {
    }
}
