package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.WebDriverRunner;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v127.fetch.Fetch;
import org.openqa.selenium.devtools.v127.network.Network;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static com.codeborne.selenide.Selenide.open;
import static steps.RuntimeState.*;

/**
 * Repair-time scenario isolation shared by the generated suite.
 *
 * The original harness inferred the initial application only from filenames
 * beginning with "admin-", while the current suite uses *-int.feature and
 * [admin] scenario names. That left many INT scenarios starting on the
 * customer origin. This hook runs after the generated default hook and binds
 * each scenario to the correct application origin before its first step.
 *
 * It also clears run-local network bookkeeping, stale downloads, and browser
 * authentication state so a later scenario cannot inherit a previous login.
 */
public final class ScenarioRepairHooks {

  @Before(order = 20_000)
  public void prepareScenario(Scenario scenario) {
    PENDING_DATA_REQUESTS.clear();
    lastDataActivityAt = 0;
    clearDefaultDownloads();

    String feature = featureName(scenario);
    boolean adminScenario = scenario.getName().startsWith("[admin]")
      || feature.endsWith("-int.feature");

    String expectedOrigin = adminScenario ? ADMIN_BASE_URL : BASE_URL;
    if (expectedOrigin == null || expectedOrigin.isBlank()) return;

    String current = currentUrl();
    if (current.isBlank() || !sameOrigin(current, expectedOrigin)) {
      open(expectedOrigin);
    }
  }

  @After(order = 20_000)
  public void cleanupScenario() {
    PENDING_DATA_REQUESTS.clear();
    lastDataActivityAt = 0;
    disableChromeFetchInterception();
    clearBrowserAuthenticationState();
  }

  private static String featureName(Scenario scenario) {
    String value = scenario.getUri() == null ? "" : scenario.getUri().toString().replace('\\', '/');
    int slash = value.lastIndexOf('/');
    return slash >= 0 ? value.substring(slash + 1) : value;
  }

  private static String currentUrl() {
    try {
      return WebDriverRunner.hasWebDriverStarted() ? WebDriverRunner.url() : "";
    } catch (Throwable ignored) {
      return "";
    }
  }

  private static boolean sameOrigin(String left, String right) {
    try {
      URI a = URI.create(left);
      URI b = URI.create(right);
      int aPort = a.getPort() >= 0 ? a.getPort() : defaultPort(a.getScheme());
      int bPort = b.getPort() >= 0 ? b.getPort() : defaultPort(b.getScheme());
      return safe(a.getScheme()).equalsIgnoreCase(safe(b.getScheme()))
        && safe(a.getHost()).equalsIgnoreCase(safe(b.getHost()))
        && aPort == bPort;
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private static int defaultPort(String scheme) {
    return "https".equalsIgnoreCase(scheme) ? 443 : "http".equalsIgnoreCase(scheme) ? 80 : -1;
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }

  private static void clearDefaultDownloads() {
    try {
      Path downloads = Path.of(Configuration.downloadsFolder).toAbsolutePath().normalize();
      Path build = Path.of("build").toAbsolutePath().normalize();
      if (!downloads.startsWith(build)) return;
      Files.createDirectories(downloads);
      try (var paths = Files.walk(downloads)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
          if (!path.equals(downloads)) Files.deleteIfExists(path);
        }
      }
    } catch (Exception ignored) {
      // Cleanup is best effort; the actual download assertion remains fail-closed.
    }
  }

  private static void disableChromeFetchInterception() {
    if (!WebDriverRunner.hasWebDriverStarted()) return;
    try {
      WebDriver driver = WebDriverRunner.getWebDriver();
      if (driver instanceof HasDevTools hasDevTools) {
        hasDevTools.getDevTools().send(Fetch.disable());
      }
    } catch (Throwable ignored) {
      // Firefox and drivers without a CDP Fetch session have nothing to disable.
    }
  }

  private static void clearBrowserAuthenticationState() {
    if (!WebDriverRunner.hasWebDriverStarted()) return;
    WebDriver driver = WebDriverRunner.getWebDriver();

    try {
      com.codeborne.selenide.Selenide.executeJavaScript(
        "try{window.localStorage.clear();}catch(e){} try{window.sessionStorage.clear();}catch(e){}");
    } catch (Throwable ignored) { }

    // WebDriver's deleteAllCookies can be origin-scoped. CDP clearBrowserCookies
    // clears the complete Chrome cookie store, preventing a customer session
    // from resurfacing after intervening admin scenarios.
    try {
      if (driver instanceof HasDevTools hasDevTools) {
        hasDevTools.getDevTools().send(Network.clearBrowserCookies());
      } else {
        driver.manage().deleteAllCookies();
      }
    } catch (Throwable ignored) {
      try { driver.manage().deleteAllCookies(); } catch (Throwable ignoredAgain) { }
    }
  }
}
