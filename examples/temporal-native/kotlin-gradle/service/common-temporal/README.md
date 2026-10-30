# ⚙️ Common Temporal

**Common Temporal** connects a service to the **Temporal server** of the stack.

## 🔧 Key Features

- **Connection defaults**: `temporal-application.yaml` targets the local dev server, `TEMPORAL_ADDRESS`
  overrides it. A service's own `application.yaml` wins over these values.
- **Kotlin payloads**: a `DataConverter` built on `jacksonObjectMapper()`, because Temporal's default
  mapper cannot build Kotlin data classes.

## 🔍 Further Details

`KotlinObjectMapperFactory` from `temporal-kotlin` 1.40.0 does not link against the Jackson of Spring
Boot 4.1, so the converter does not use it. Workers and clients come from Temporal's own
`temporal-spring-boot-starter`.
