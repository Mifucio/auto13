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

public final class NetworkMockSupport {

  static void resetScenarioNetworkState() {
    // Consume any events left by the previous scenario before resetting its
    // request lifecycle. New requests triggered by the scenario's initial
    // navigation are recorded by the next BeforeStep drain.
    drainPerformanceLogs();
    PENDING_DATA_REQUESTS.clear();
    lastDataActivityAt = 0;
    NetworkBusinessWaitRepair.resetRenderedEvidence();
  }

  static void drainPerformanceLogs() {
    if (!WebDriverRunner.hasWebDriverStarted()) return;
    try {
      for (LogEntry entry : WebDriverRunner.getWebDriver().manage().logs().get(LogType.PERFORMANCE)) {
        JsonObject envelope = JsonParser.parseString(entry.getMessage()).getAsJsonObject();
        JsonObject message = envelope.getAsJsonObject("message");
        if (message == null || !message.has("method")) continue;
        String method = message.get("method").getAsString();
        JsonObject params = message.getAsJsonObject("params");
        if (params == null || !params.has("requestId")) continue;
        String requestId = params.get("requestId").getAsString();
        if ("Network.requestWillBeSent".equals(method)) {
          String type = params.has("type") ? params.get("type").getAsString() : "";
          if (!"XHR".equalsIgnoreCase(type) && !"Fetch".equalsIgnoreCase(type)) continue;
          JsonObject request = params.getAsJsonObject("request");
          String url = request != null && request.has("url") ? request.get("url").getAsString() : "unknown";
          String requestMethod = request != null && request.has("method") ? request.get("method").getAsString() : "";
          // Performance logs are drained after a step, so this event may have
          // occurred well before it is processed here. Preserve the browser log
          // timestamp so Resource Timing completion evidence can be correlated
          // with the real request start instead of the later drain time.
          long requestStartedAt = entry.getTimestamp();
          if (requestStartedAt <= 0) requestStartedAt = System.currentTimeMillis();
          PENDING_DATA_REQUESTS.put(requestId, new PendingRequest(url, requestMethod, requestStartedAt));
          lastDataActivityAt = System.currentTimeMillis();
        } else if ("Network.responseReceived".equals(method)) {
          PendingRequest pending = PENDING_DATA_REQUESTS.get(requestId);
          JsonObject response = params.getAsJsonObject("response");
          if (pending != null && response != null && response.has("status")) {
            pending.responseStatus = response.get("status").getAsInt();
            long responseReceivedAt = entry.getTimestamp();
            pending.responseReceivedAt = responseReceivedAt > 0
              ? responseReceivedAt : System.currentTimeMillis();
          }
        } else if ("Network.loadingFinished".equals(method) || "Network.loadingFailed".equals(method)) {
          PendingRequest pending = PENDING_DATA_REQUESTS.remove(requestId);
          if (pending != null) {
            long completedAt = entry.getTimestamp();
            if (completedAt <= 0) completedAt = System.currentTimeMillis();
            long durationMs = Math.max(0, completedAt - pending.startedAt);
            PERFORMANCE_RESULTS.add("{\"type\":\"external-data\",\"url\":\"" + jsonEscape(pending.url) + "\",\"durationMs\":" + durationMs + ",\"slow\":" + (durationMs > EXTERNAL_SLOW_MS) + "}");
            lastDataActivityAt = System.currentTimeMillis();
          }
        }
      }
    } catch (Exception e) {
      System.err.println("  ⚠️  Performance log read failed: " + e.getMessage());
    }
  }

  /**
   * Prove completion of one exact Chrome request when the performance log has a
   * responseReceived event but omits loadingFinished. CDP exposes the response
   * body only after the body is fully available, and the request id prevents a
   * duplicate same-URL request from satisfying this probe.
   */
  static boolean hasCompletedResponseBody(String requestId) {
    if (requestId == null || requestId.isBlank() || !WebDriverRunner.hasWebDriverStarted()) return false;
    WebDriver driver = WebDriverRunner.getWebDriver();
    if (!(driver instanceof org.openqa.selenium.devtools.HasDevTools hasDevTools)) return false;
    try {
      org.openqa.selenium.devtools.DevTools devTools = hasDevTools.getDevTools();
      devTools.createSessionIfThereIsNotOne();
      devTools.send(org.openqa.selenium.devtools.v127.network.Network.getResponseBody(
        new org.openqa.selenium.devtools.v127.network.model.RequestId(requestId)));
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  static void waitForExternalData() {
    long startedAt = System.currentTimeMillis();
    while (true) {
      drainPerformanceLogs();
      long now = System.currentTimeMillis();
      boolean quiet = PENDING_DATA_REQUESTS.isEmpty() && (lastDataActivityAt == 0 || now - lastDataActivityAt >= NETWORK_QUIET_MS);
      if (quiet) return;
      if (now - startedAt >= HANG_TIMEOUT_MS) {
        String pendingUrls = PENDING_DATA_REQUESTS.values().stream().map(request -> request.url).distinct().collect(Collectors.joining(", "));
        throw new AssertionError("HANG_DETECTED external data exceeded " + HANG_TIMEOUT_MS + "ms; pending URLs: " + pendingUrls);
      }
      try { Thread.sleep(50); } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("HANG_DETECTED external data wait interrupted", e);
      }
    }
  }

  /** Intercept matching URLs and answer with the given HTTP status.
   *  Firefox uses BiDi Network; Chrome uses CDP Fetch. Best-effort:
   *  throws when the interception cannot be set up. */
  static void mockHttpStatus(String urlPattern, int status) {
    if ("firefox".equalsIgnoreCase(System.getenv("OHTEST_BROWSER"))) {
      mockHttpStatusFirefox(urlPattern, status);
    } else {
      mockHttpStatusChrome(urlPattern, status);
    }
  }

  /** Deactivate and remove any network mock installed by this scenario. */
  static void teardownHttpMock() {
    mockActive = false;
    if (mockNetwork != null && mockInterceptId != null) {
      try {
        mockNetwork.removeIntercept(mockInterceptId);
      } catch (Throwable ignored) { }
    }
    mockNetwork = null;
    mockInterceptId = null;
  }

  static void mockHttpStatusFirefox(String urlPattern, int status) {
    try {
      org.openqa.selenium.bidi.module.Network network =
        new org.openqa.selenium.bidi.module.Network(WebDriverRunner.getWebDriver());
      org.openqa.selenium.bidi.network.AddInterceptParameters intercept =
        new org.openqa.selenium.bidi.network.AddInterceptParameters(
          org.openqa.selenium.bidi.network.InterceptPhase.BEFORE_REQUEST_SENT);
      if (urlPattern.equals(".*")) {
        // Gecko BiDi rejects bare '*' and '.*' patterns; an empty UrlPattern
        // (all fields optional) matches every URL.
        intercept.urlPattern(new org.openqa.selenium.bidi.network.UrlPattern());
      } else {
        // Gecko URL patterns reject bare '*'; the wildcard is written as '\*'.
        intercept.urlStringPattern(urlPattern.replace("*", "\\*"));
      }
      mockNetwork = network;
      mockInterceptId = network.addIntercept(intercept);
      mockActive = true;
      network.onBeforeRequestSent(event -> {
        if (!mockActive) {
          return; // mock torn down (previous scenario); ignore events
        }
        String requestId = event.getRequest().getRequestId();
        // Mock API/XHR calls only; let page navigations (Accept: text/html)
        // load normally so the login page itself still renders. An intercepted
        // request stays BLOCKED until continueRequest/provideResponse is sent,
        // so the document must be explicitly continued.
        boolean isDocument = event.getRequest().getHeaders().stream()
          .anyMatch(h -> h.getName().equalsIgnoreCase("accept")
            && h.getValue().getValue().toLowerCase().contains("text/html"));
        if (isDocument) {
          network.continueRequest(new org.openqa.selenium.bidi.network.ContinueRequestParameters(requestId));
          return;
        }
        // "Authentication service is down" mocks target the sign-in call:
        // page bootstrapping GETs (config, session) must keep working or the
        // login button never renders. Mock only POSTs.
        if (!"POST".equalsIgnoreCase(event.getRequest().getMethod())) {
          network.continueRequest(new org.openqa.selenium.bidi.network.ContinueRequestParameters(requestId));
          return;
        }
        // Best effort: a request may already be gone (navigation aborted,
        // another intercept continued it); failing the whole test on that
        // race would mask the actual scenario outcome.
        try {
          network.provideResponse(
            new org.openqa.selenium.bidi.network.ProvideResponseParameters(requestId)
              .statusCode(status)
              .reasonPhrase("Mocked")
              .body(new org.openqa.selenium.bidi.network.BytesValue(
                org.openqa.selenium.bidi.network.BytesValue.Type.STRING,
                "{\"message\":\"Service temporarily unavailable\"}")));
        } catch (Throwable t) {
          System.out.println("  [mock] skip " + event.getRequest().getUrl() + ": " + t.getMessage());
          try {
            network.continueRequest(new org.openqa.selenium.bidi.network.ContinueRequestParameters(requestId));
          } catch (Throwable ignored) { }
        }
      });
      System.out.println("  🔀 BiDi mock (Firefox): " + urlPattern + " -> HTTP " + status);
    } catch (Throwable t) {
      throw new AssertionError("BiDi network mock unavailable: " + t.getMessage());
    }
  }

  static void mockHttpStatusChrome(String urlPattern, int status) {
    try {
      org.openqa.selenium.devtools.DevTools devTools =
        ((org.openqa.selenium.devtools.HasDevTools) WebDriverRunner.getWebDriver()).getDevTools();
      devTools.createSessionIfThereIsNotOne();
      org.openqa.selenium.devtools.v127.fetch.model.RequestPattern pattern =
        new org.openqa.selenium.devtools.v127.fetch.model.RequestPattern(
          java.util.Optional.of(urlPattern),
          java.util.Optional.empty(),
          java.util.Optional.empty());
      devTools.send(org.openqa.selenium.devtools.v127.fetch.Fetch.enable(
        java.util.Optional.of(java.util.List.of(pattern)),
        java.util.Optional.empty()));
      devTools.addListener(org.openqa.selenium.devtools.v127.fetch.Fetch.requestPaused(), event -> {
        if (!mockActive) {
          // Mock torn down; let the request continue normally.
          try {
            devTools.send(org.openqa.selenium.devtools.v127.fetch.Fetch.continueRequest(
              event.getRequestId(), java.util.Optional.empty(), java.util.Optional.empty(),
              java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty()));
          } catch (Throwable ignored) { }
          return;
        }
        devTools.send(org.openqa.selenium.devtools.v127.fetch.Fetch.fulfillRequest(
          event.getRequestId(),
          status,
          java.util.Optional.of(java.util.List.of(
            new org.openqa.selenium.devtools.v127.fetch.model.HeaderEntry("Content-Type", "application/json"))),
          java.util.Optional.of("Mocked"),
          java.util.Optional.of("{\"message\":\"Service temporarily unavailable\"}"),
          java.util.Optional.empty()));
      });
      System.out.println("  🔀 CDP mock (Chrome): " + urlPattern + " -> HTTP " + status);
    } catch (Throwable t) {
      throw new AssertionError("CDP network mock unavailable: " + t.getMessage());
    }
  }
}
