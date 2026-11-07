# ⚙️ Common Zeebe

**Common Zeebe** holds what the service still needs to know about Zeebe directly. Everything else goes through
the process-engine-api, see [ADR-0014](../../../docs/adr/0014-process-engine-api-on-camunda-8.md).

## 🔧 Key Features

- **Zeebe Client Connection**: `zeebe-application.yaml` contributes the self-managed connection defaults
  (gRPC `localhost:26500`, REST `localhost:8080`, no auth) and the message time-to-live through
  `ZeebeEnvironmentConfiguration`.
- **Auto-deployment**: `EngineAutoConfiguration` (registered via `META-INF/spring/…AutoConfiguration.imports`)
  deploys every `bpmn/*.bpmn` and `dmn/*.dmn` on the classpath at start-up.

## 📌 How to Use

Include **common-zeebe** as a dependency of your service module.
