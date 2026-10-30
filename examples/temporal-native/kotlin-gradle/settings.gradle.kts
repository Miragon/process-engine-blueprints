pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "temporal-example"

include("service:app")
include("service:common-architecture-tests")
include("service:common-temporal")
include("service:common-temporal-test")
