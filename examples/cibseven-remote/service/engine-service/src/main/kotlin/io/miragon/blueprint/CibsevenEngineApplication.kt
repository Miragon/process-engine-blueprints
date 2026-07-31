package io.miragon.blueprint

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * The remote CIB seven engine host. Boots the embedded engine, deploys the process contract from
 * `common-package`, and exposes `/engine-rest` and the Cockpit/Tasklist at `/camunda`. All service-task
 * logic lives in the separate `example-service` worker and is consumed as external tasks.
 */
@SpringBootApplication
class CibsevenEngineApplication

fun main(args: Array<String>) {
    runApplication<CibsevenEngineApplication>(*args)
}
