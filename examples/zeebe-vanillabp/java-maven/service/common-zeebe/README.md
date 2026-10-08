# 🔌 Common Zeebe

**Common Zeebe** is the backbone module for running a service's BPMN workflows on the **Zeebe process
engine**. It is the only place that knows the engine: services that depend on it compile against the
[VanillaBP SPI](https://github.com/vanillabp/spi-for-java) and never touch a Camunda API.

## 🔧 What it contributes

- **Zeebe connection defaults** — `zeebe-application.yaml` (self-managed, gRPC 26500 / REST 8080, auth
  off for local dev, a 10s message time-to-live), imported by `EngineAutoConfiguration`.
- **The VanillaBP Camunda 8 adapter** — on the runtime classpath only. It deploys the BPMN and DMN
  models of the service's workflow module, opens the job workers behind the `@WorkflowTask` methods
  and implements `ProcessService`.

## 🔍 Further details

The adapter is built against the Camunda **8.8** client line (`camunda-spring-boot-4-starter`); an 8.9
client breaks it at startup, so this module pins the client the adapter ships with. An 8.8 client
against an 8.9 cluster is the upgrade order Camunda supports.

The service itself configures the workflow module in its `application.yaml`:

```yaml
workerId: ${WORKER_ID:local}

vanillabp:
  workflow-modules:
    <spring.application.name>:
      adapters:
        camunda8:
          resources-location: classpath:/bpmn
          use-tenants: false
```

## 📌 How to use

Depend on it with `runtime` scope, so neither the Camunda client nor the adapter reaches the compile
classpath of the service:

```xml
<dependency>
    <groupId>io.miragon.blueprint</groupId>
    <artifactId>common-zeebe</artifactId>
    <version>${project.version}</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.vanillabp</groupId>
    <artifactId>spi-for-java</artifactId>
</dependency>
```
