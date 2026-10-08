package io.miragon.blueprint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The remote Operaton engine host. Boots the engine and exposes {@code /engine-rest} and the
 * Cockpit/Tasklist at {@code /operaton}. It ships no model of its own — the separate
 * {@code example-service} owns the process and deploys it over REST — and all service-task logic runs
 * in that worker as external tasks. The one exception is <b>execution/task listeners</b>
 * ({@code io.miragon.blueprint.listener}): those have no external-task equivalent and run inside the
 * engine, so their beans live here.
 */
@SpringBootApplication
public class OperatonEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(OperatonEngineApplication.class, args);
    }
}
