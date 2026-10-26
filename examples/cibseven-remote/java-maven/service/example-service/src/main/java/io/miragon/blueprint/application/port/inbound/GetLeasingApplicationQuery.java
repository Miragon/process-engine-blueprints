package io.miragon.blueprint.application.port.inbound;

import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public interface GetLeasingApplicationQuery {
    Optional<Result> byId(ApplicationId id);

    /** The application together with the model of its bike, resolved from the portfolio. */
    record Result(
            LeasingApplication application,
            @Nullable String bikeModel
    ) {
    }
}
