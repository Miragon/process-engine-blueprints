package io.miragon.blueprint

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * The remote Operaton engine host. Boots the engine and exposes `/engine-rest` and the
 * Cockpit/Tasklist at `/operaton`. It ships no model of its own — the separate `example-service` owns
 * the process and deploys it over REST — and all service-task logic runs in that worker as external
 * tasks. The one exception is **execution/task listeners** (`io.miragon.blueprint.listener`): those
 * have no external-task equivalent and run inside the engine, so their beans live here.
 */
@SpringBootApplication
class OperatonEngineApplication

fun main(args: Array<String>) {
    runApplication<OperatonEngineApplication>(*args)
}
