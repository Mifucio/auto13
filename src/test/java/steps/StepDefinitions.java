package steps;

import com.codeborne.selenide.*;
import com.codeborne.selenide.logevents.SelenideLogger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.cucumber.java.*;
import io.cucumber.java.BeforeStep;
import io.cucumber.java.AfterStep;
import io.cucumber.java.en.*;
import io.qameta.allure.Allure;
import io.qameta.allure.Attachment;
import io.qameta.allure.selenide.AllureSelenide;
import regression.CheckpointCapture;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LoggingPreferences;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.logging.Level;

import static com.codeborne.selenide.Selenide.*;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Condition.*;
import static steps.RuntimeState.*;
import static steps.AuthSupport.*;
import static steps.NetworkMockSupport.*;

public class StepDefinitions {

  @Before
  public void beforeScenario(Scenario scenario) {
    CONSOLE_LOGS.clear();
    SCENARIO_CHECKPOINT_OCCURRENCES.clear();
    scenarioStartedAt = System.currentTimeMillis();
    currentScenarioName = scenario.getName();
    String scenarioUri = scenario.getUri().toString().replace('\\', '/');
    int lastSlash = scenarioUri.lastIndexOf('/');
    currentFeatureFile = lastSlash >= 0 ? scenarioUri.substring(lastSlash + 1) : scenarioUri;
    // Bind the Selenide webdriver to this thread BEFORE any step runs:
    // Selenide throws "No webdriver is bound to current thread" when a step
    // calls $()/byText() before open(). Admin scenarios navigate themselves
    // via adminOpen(); everything else starts at the customer application.
    if (ADMIN_BASE_URL != null && !ADMIN_BASE_URL.isEmpty() && currentFeatureFile.startsWith("admin-")) {
      open(ADMIN_BASE_URL);
    } else if (BASE_URL != null && !BASE_URL.isEmpty()) {
      open(BASE_URL);
    } else {
      // Even structural/offline suites need a bound browser before the shared
      // console instrumentation and generic body check run. about:blank keeps
      // this path deterministic and does not contact an untrusted target.
      open("about:blank");
    }
    // Intercept browser console.log via JavaScript
    executeJavaScript(
      "window.__testConsole = [];" +
      "const origLog = console.log;" +
      "console.log = function() { " +
      "  window.__testConsole.push(Array.from(arguments).join(' ')); " +
      "  origLog.apply(console, arguments); " +
      "};" +
      "const origErr = console.error;" +
      "console.error = function() { " +
      "  window.__testConsole.push('[ERROR] ' + Array.from(arguments).join(' ')); " +
      "  origErr.apply(console, arguments); " +
      "};" +
      "const origWarn = console.warn;" +
      "console.warn = function() { " +
      "  window.__testConsole.push('[WARN] ' + Array.from(arguments).join(' ')); " +
      "  origWarn.apply(console, arguments); " +
      "};"
    );
  }

  @BeforeStep
  public void beforeStep(Scenario scenario) {
    drainPerformanceLogs();
    stepStartedAt = System.currentTimeMillis();
    currentStep = scenario.getName();
  }

  @AfterStep
  public void afterStep(Scenario scenario) {
    waitForExternalData();
    long durationMs = System.currentTimeMillis() - stepStartedAt;
    boolean slow = durationMs > SLOW_STEP_MS;
    PERFORMANCE_RESULTS.add("{\"type\":\"step\",\"name\":\"" + jsonEscape(currentStep) + "\",\"durationMs\":" + durationMs + ",\"slow\":" + slow + "}");
    // Observability: per-step NDJSON event (pod-local mirror; the entrypoint
    // exports it). One line per step, JSON, no PII beyond step text.
    writeObservabilityEvent("step", currentStep, durationMs, slow, null);
  }

  @After
  public void afterScenario(Scenario scenario) {
    teardownHttpMock();
    long scenarioDurationMs = System.currentTimeMillis() - scenarioStartedAt;
    PERFORMANCE_RESULTS.add("{\"type\":\"scenario\",\"name\":\"" + jsonEscape(scenario.getName())
      + "\",\"durationMs\":" + scenarioDurationMs + ",\"failed\":" + scenario.isFailed() + "}");
    writePerformanceReport();
    // Observability: per-scenario NDJSON event with the final status.
    writeObservabilityEvent("scenario", scenario.getName(), scenarioDurationMs,
      scenario.isFailed(), scenario.isFailed() ? "scenario failed" : null);
    if (scenario.isFailed()) {
      // Screenshot via Selenide
      try {
        String screenshotPath = Selenide.screenshot("failure_" + scenario.getName().replaceAll("[^a-zA-Z0-9_]", "_"));
        if (screenshotPath != null) {
          Allure.addAttachment("Screenshot", "image/png",
            new java.io.ByteArrayInputStream(
              new java.io.FileInputStream(screenshotPath).readAllBytes()
            ), ".png");
        }
      } catch (Exception e) {
        System.err.println("  ⚠️  Screenshot failed: " + e.getMessage());
      }

      // Console logs → Allure attachment
      try {
        String logs = getConsoleLogs();
        if (!logs.isEmpty()) {
          Allure.addAttachment("Console Logs", "text/plain", logs);
        }
      } catch (Exception e) {
        System.err.println("  ⚠️  Log attach failed: " + e.getMessage());
      }

      // Browser driver console logs (WebDriver API)
      try {
        List<String> driverLogs = getDriverConsoleLogs();
        if (!driverLogs.isEmpty()) {
          String driverLog = String.join("\\n", driverLogs);
          Allure.addAttachment("Browser Console (WebDriver)", "text/plain", driverLog);
        }
      } catch (Exception e) {
        // Browser may not support or already closed
      }
    }
  }

  @BeforeAll
  public static void beforeAll() {
    PERFORMANCE_RESULTS.clear();
    suiteStartedAt = System.currentTimeMillis();
    try { Files.deleteIfExists(Path.of("reports", "performance-results.json")); } catch (Exception ignored) { }
    System.out.println("🚀 Java + Selenide suite starting");
  }

  @AfterAll
  public static void afterAll() {
    if (WebDriverRunner.hasWebDriverStarted()) {
      closeWebDriver();
    }
    writePerformanceReport();
    System.out.println("✅ Suite completed");
  }

  // ── Locator Constants ─────────────────────────────────────────
  // No locators generated — tests use generic actions

  // ── Shared/common Step Definitions ─────────────────────────────


}