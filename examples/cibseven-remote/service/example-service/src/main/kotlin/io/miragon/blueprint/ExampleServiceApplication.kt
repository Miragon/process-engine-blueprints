package io.miragon.blueprint

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.client.RestClient
import java.time.Clock

/**
 * The remote worker + business logic. It hosts the domain, use cases and adapters, subscribes to the
 * engine's external service-tasks over the external-task client, and drives the process (start, user
 * tasks, messages) over the engine's REST API. The CIB seven engine itself runs in `engine-service`.
 */
@SpringBootApplication
class ExampleServiceApplication {

    /** Single source of "now" for the app, so time-dependent logic can be pinned in tests. */
    @Bean
    fun clock(): Clock = Clock.systemDefaultZone()

    /**
     * [RestClient] pointed at the remote CIB seven engine's REST API — used by the outbound engine
     * adapter to start the process, complete user tasks and correlate messages.
     */
    @Bean
    fun engineRestClient(
        @Value("\${bike-leasing.engine-rest-base-url}") baseUrl: String,
    ): RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build()
}

fun main(args: Array<String>) {
    runApplication<ExampleServiceApplication>(*args)
}
