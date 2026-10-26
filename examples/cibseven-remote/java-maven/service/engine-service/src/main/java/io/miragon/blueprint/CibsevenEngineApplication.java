package io.miragon.blueprint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The remote CIB seven engine host. Boots the engine and exposes {@code /engine-rest} and the
 * Cockpit/Tasklist at {@code /camunda}. It ships no model of its own — the separate {@code example-service} owns
 * the process and deploys it over REST — and all service-task logic runs in that worker as external
 * tasks. The one exception is <b>execution/task listeners</b> ({@code io.miragon.blueprint.listener}): those
 * have no external-task equivalent and run inside the engine, so their beans live here.
 */
@SpringBootApplication
public class CibsevenEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(CibsevenEngineApplication.class, args);
    }
}
