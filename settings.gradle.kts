// Toolchain auto-provisioning (foojay resolver) lets Gradle download a
// JDK 25 when it is not already installed, so the suite
// builds and runs on the pinned Java 25.
plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "test-suite"
