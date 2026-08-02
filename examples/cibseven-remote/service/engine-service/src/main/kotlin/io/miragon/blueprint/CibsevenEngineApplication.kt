package io.miragon.blueprint

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * The remote CIB seven engine host. Boots the engine and exposes `/engine-rest` and the
 * Cockpit/Tasklist at `/camunda`. It ships no model of its own — the separate `example-service` owns
 * the process and deploys it over REST — and all service-task logic runs in that worker as external
 * tasks. The one exception is **execution/task listeners** (`io.miragon.blueprint.listener`): those
 * have no external-task equivalent and run inside the engine, so their beans live here.
 */
@SpringBootApplication
class CibsevenEngineApplication

fun main(args: Array<String>) {
    runApplication<CibsevenEngineApplication>(*args)
}
