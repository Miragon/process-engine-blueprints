# 🧪 common-zeebe-test

**Common Zeebe Test** provides shared assertions for testing BPMN processes with Camunda Platform 8.

## 📌 What's Included

- **CamundaAssertExtensions**: lets `CamundaAssert` take the generated `FlowNode`s and `ProcessPath`s of
  bpmn-to-code instead of raw element ids.

## 🔧 How to Use

Include **common-zeebe-test** as a test dependency:

```kotlin
// In build.gradle.kts
dependencies {
    testImplementation(project(":service:common-zeebe-test"))
}
```
