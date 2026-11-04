pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "zeebe-process-engine-api-example"

include("service:app")
include("service:common-architecture-tests")
include("service:common-zeebe")
include("service:common-zeebe-test")
