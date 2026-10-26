# Which example do I need?

All examples automate the same bike-leasing process with the same domain, REST API and test scenarios, so
you can compare them line by line. Four questions get you to one.

## 1. Camunda 7 world or Camunda 8 world?

- **Camunda 7 compatible** (BPMN with `camunda:` extensions, JavaDelegates or external tasks, Cockpit and
  Tasklist): continue with question 2.
- **Camunda 8 / Zeebe** (a broker, job workers, Operate and Tasklist as separate apps):
  - [`zeebe-native`](../examples/zeebe-native/README.md) uses the Camunda Spring SDK directly.
  - [`zeebe-vanillabp`](../examples/zeebe-vanillabp/README.md) goes through VanillaBP, an engine-neutral
    abstraction.

## 2. Which Camunda 7 fork?

| Engine | Pick it when |
|---|---|
| **CIB seven** | you want a commercially backed fork with long-term support |
| **Operaton** | you want the community fork under the Apache licence |
| **Fluxnova** | your organisation standardises on Fluxnova |

## 3. Engine inside the service, or as its own process?

This is the **stack option** (the *Setup* column in the [README](../README.md#examples)):

- **Embedded:** the engine runs inside your Spring Boot service, next to Postgres. One deployable, the simplest
  operations, service tasks as JavaDelegates. → `<engine>-embedded`
- **Remote:** a generic engine host runs the engine; your service owns the model and runs its logic as external
  tasks. Engine and service scale and deploy independently. → `<engine>-remote`

## 4. Talk to the engine directly, or through an abstraction?

- **Directly** with the engine's own API: `<engine>-embedded` or `<engine>-remote`.
- **Through the bpm-crafters [process-engine-api](https://github.com/bpm-crafters)**, which keeps your code
  portable between engines: `<engine>-process-engine-api`.

## And the UI?

[`fullstack-cibseven-embedded`](../examples/fullstack-cibseven-embedded/README.md) adds a React front office and
back office and the most complete agent tooling. It is the reference the other examples follow, in
Kotlin/Gradle only.

## Kotlin or Java?

Every engine example comes in both. Pick **Kotlin + Gradle** when you are free to choose, **Java + Maven** when
that is your team's standard or you are in a training. Both run the same process behind the same REST contract.
