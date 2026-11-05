package io.miragon.blueprint.adapter.inbound.rest;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the customer-portal / back-office contract this worker serves at
 * {@code /v3/api-docs}. That contract is committed at {@code openapi/openapi.json} and consumed by any
 * API client (see docs/adr/0003); the engine-internal {@code /engine-rest} API is a separate concern
 * owned by the engine-service and driven here through the generated client, not re-exposed.
 *
 * <p>This is cross-cutting web configuration and lives in {@code adapter.inbound.rest}, NOT in a
 * separate {@code config} package — the architecture tests ignore only direct members of the root
 * package, so a new {@code io.miragon.blueprint.config} package would fail the suite. The
 * {@code Configuration} suffix is whitelisted for this package in {@code NamingConventionArchitectureTest}.
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI bikeLeasingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MiraVelo Bike-Leasing API")
                        .version("1.0")
                        .description(
                                "Customer-portal and back-office endpoints for the MiraVelo bike-leasing "
                                        + "process. The engine-internal /engine-rest API is intentionally excluded."));
    }
}
