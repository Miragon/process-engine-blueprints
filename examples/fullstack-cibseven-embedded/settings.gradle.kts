rootProject.name = "fullstack-example"

include("service:common-architecture-tests")
include("service:app")
// frontend/ is npm-only on purpose — see docs/adr/0005-frontend-stays-out-of-the-gradle-build.md
