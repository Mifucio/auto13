plugins {
  id("java")
}

// Strict pin: the generated suite is verified against the wrapper-pinned
// Gradle 9.7.0. Do not run it on an older, unverified
// Gradle.
require(org.gradle.util.GradleVersion.current() >= org.gradle.util.GradleVersion.version("9.7.0")) {
  "Generated Java projects require Gradle 9.7.0 or newer; found ${org.gradle.util.GradleVersion.current()}"
}

group = "com.test"
version = "1.0.0"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

// Emit bytecode for the same Java 25 release regardless of
// the toolchain, so the suite runs on the pinned JDK 25.
tasks.withType<JavaCompile> {
  options.release.set(25)
}

repositories {
  mavenCentral()
}

val selenideVersion = "7.4.3"
val cucumberVersion = "7.20.1"
val allureVersion = "2.29.0"
val jUnitPlatformVersion = "1.11.3"

dependencies {
  // Selenide
  testImplementation("com.codeborne:selenide:${selenideVersion}")
  testImplementation("com.google.code.gson:gson:2.11.0")

  // CDP (Fetch mock, performance logs) — selenium-java pulls the devtools
  // artifacts only as runtime; pin the matching protocol version for compile.
  testImplementation("org.seleniumhq.selenium:selenium-devtools-v127:4.24.0")

  // Cucumber
  testImplementation("io.cucumber:cucumber-java:${cucumberVersion}")
  testImplementation("io.cucumber:cucumber-junit-platform-engine:${cucumberVersion}")
  testImplementation("io.cucumber:cucumber-picocontainer:${cucumberVersion}")

  // Allure result libraries. The optional CLI is intentionally not a test
  // runtime dependency because a dual-runtime container must run with a
  // network-disabled runtime after image build.
  testImplementation("io.qameta.allure:allure-cucumber7-jvm:${allureVersion}")
  testImplementation("io.qameta.allure:allure-selenide:${allureVersion}")

  // JUnit Platform
  testImplementation("org.junit.platform:junit-platform-suite:${jUnitPlatformVersion}")
  testImplementation("org.junit.platform:junit-platform-engine:${jUnitPlatformVersion}")
  testImplementation("org.junit.platform:junit-platform-launcher:${jUnitPlatformVersion}")
}

tasks.test {
  useJUnitPlatform()
  // Chrome (headless) listens on 127.0.0.1; force IPv4 so the JVM connects
  // to the DevTools WebSocket instead of resolving localhost to ::1.
  jvmArgs("-Djava.net.preferIPv4Stack=true")
  systemProperty("allure.results.directory", file("build/allure-results").absolutePath)

  // ── Cucumber tag-scoping ──────────────────────────────────────
  // `-Pcucumber.filter.tags=@tag` (or `-Dcucumber.filter.tags=@tag`) scopes the
  // run to scenarios carrying the given tag.  Forward to the forked test JVM so
  // the JUnit Platform engine can apply the filter.
  //
  // Usage:
  //   ./gradlew test -Pcucumber.filter.tags=@direct_dokobit_mobile_id
  val filterTags = (System.getProperty("cucumber.filter.tags")?.takeIf { it.isNotBlank() }
    ?: providers.gradleProperty("cucumber.filter.tags").orNull?.takeIf { it.isNotBlank() })
  if (filterTags != null) {
    systemProperty("cucumber.filter.tags", filterTags)
    println("cucumber.filter.tags=$filterTags")
  }
}

// Used by the dual-runtime image build to materialize every test runtime jar
// while the build context still has network access. Runtime execution is then
// deliberately network-independent.
tasks.register("resolveTestRuntimeClasspath") {
  doLast {
    configurations.testRuntimeClasspath.get().files.forEach { it.length() }
  }
}

// Isolate WebDriver/ChromeDriver startup from credentials, mTLS and SETS. Run
// this before a full live suite when the failure is SessionNotCreatedException.
tasks.register<JavaExec>("browserSmoke") {
  dependsOn(tasks.compileTestJava)
  classpath = sourceSets["test"].runtimeClasspath
  mainClass.set("runner.BrowserSmoke")
  jvmArgs("-Djava.net.preferIPv4Stack=true")
}

// The Allure CLI is an optional operator concern and is not put on the test
// runtime classpath. Install it separately when a local HTML report is needed.

tasks.register<JavaExec>("regressionCapture") {
  dependsOn(tasks.compileTestJava)
  classpath = sourceSets["test"].runtimeClasspath
  mainClass.set("regression.BaselineManager")
  args("capture")
  systemProperty("runId", providers.gradleProperty("runId").orElse("local").get())
  systemProperty("baselineId", providers.gradleProperty("baselineId").orElse("candidate-local").get())
}

tasks.register<JavaExec>("regressionApprove") {
  dependsOn(tasks.compileTestJava)
  classpath = sourceSets["test"].runtimeClasspath
  mainClass.set("regression.BaselineManager")
  args("approve")
  systemProperty("candidateId", providers.gradleProperty("candidateId").orElse(providers.gradleProperty("baselineId").orElse("")).get())
  systemProperty("baselineId", providers.gradleProperty("baselineId").orElse("").get())
  systemProperty("approvedBy", providers.gradleProperty("approvedBy").orElse(System.getProperty("user.name", "unknown")).get())
  systemProperty("applicationVersion", providers.gradleProperty("applicationVersion").orElse("unknown").get())
  systemProperty("gitRevision", providers.gradleProperty("gitRevision").orElse("working-tree").get())
  systemProperty("suiteRevision", providers.gradleProperty("suiteRevision").orElse("working-tree").get())
}

tasks.register<JavaExec>("regressionCompare") {
  dependsOn(tasks.compileTestJava)
  classpath = sourceSets["test"].runtimeClasspath
  mainClass.set("regression.BaselineManager")
  args("compare")
  systemProperty("runId", providers.gradleProperty("runId").orElse("local").get())
  systemProperty("baselineId", providers.gradleProperty("baselineId").orElse("").get())
  systemProperty("visualThreshold", providers.gradleProperty("visualThreshold").orElse("0.02").get())
  systemProperty("colorTolerance", providers.gradleProperty("colorTolerance").orElse("0").get())
}

tasks.register<JavaExec>("regressionReport") {
  dependsOn(tasks.compileTestJava)
  classpath = sourceSets["test"].runtimeClasspath
  mainClass.set("regression.RegressionReport")
  systemProperty("runId", providers.gradleProperty("runId").orElse("local").get())
  systemProperty("baselineId", providers.gradleProperty("baselineId").orElse("").get())
}

