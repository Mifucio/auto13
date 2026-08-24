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
  value = "pretty, runner.FailureLoggingPlugin, json:target/allure-results/cucumber-report.json, io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm")
@ConfigurationParameter(key = "cucumber.publish.quiet", value = "true")
public class TestRunner {
  // JUnit Platform Suite will discover and run all Cucumber features
}
