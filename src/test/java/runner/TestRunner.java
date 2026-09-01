package runner;

import org.junit.platform.suite.api.*;
import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * Cucumber Test Runner — discovers feature files and runs step definitions.
 *
 * Run with Gradle 9.7.0: gradle test
 * Allure report: gradle allureReport → build/reports/allure-report/index.html
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "steps")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
  value = "pretty, runner.FailureLoggingPlugin, runner.TimelineMetricsPlugin, runner.TemporaryPerfAnalyzer, json:target/allure-results/cucumber-report.json, io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm")
@ConfigurationParameter(key = "cucumber.publish.quiet", value = "true")
// Retry any failed scenario once (flaky-proofing for network hiccups/timeouts).
// The retry restores saved cookies on the second attempt too, so a transient
// session expiry on the first try falls through to normal login on retry.
@ConfigurationParameter(key = "cucumber.execution.retry.maxAttempts", value = "1")
public class TestRunner {
  // JUnit Platform Suite will discover and run all Cucumber features
}
