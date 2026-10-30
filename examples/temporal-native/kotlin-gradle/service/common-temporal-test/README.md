# 🧪 common-temporal-test

**Common Temporal Test** provides helpers for process tests on `TestWorkflowEnvironment`.

## 📌 What's Included

- **`awaitActivitySettled` / `awaitCancelRequested`**: synchronise a test with the workflow's history.
  The test server fails a workflow task that cancels an activity completing at that very moment, so a
  test that cancels has to wait for parallel steps first.

## 🔧 How to Use

```kotlin
// In build.gradle.kts
dependencies {
    testImplementation(project(":service:common-temporal-test"))
}
```
