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
import java.util.Set;

import static com.codeborne.selenide.Selenide.open;
import static steps.RuntimeState.*;

/**
 * Repair-time scenario isolation shared by the generated suite.
 *
 * Business data remains scenario-scoped, but authentication is intentionally
 * reused between ordinary scenarios. A real Mobile-ID/Dokobit login costs
 * roughly 30-70 seconds in the live environment; deleting the complete Chrome
 * cookie store after every scenario made the suite spend most of its wall time
 * re-authenticating instead of testing business behavior.
 *
 * Scenarios whose purpose includes login/company-selection still start from a
 * clean browser auth state. Cross-surface helpers explicitly clear auth when
 * switching customer -> admin, so preserving sessions here does not blur the
 * two application origins.
 */
public final class ScenarioRepairHooks {
  private static final Set<String> FRESH_AUTH_FEATURES = Set.of(
    "user-login-via-dokobit-smart-id-or-mobile-id.feature",
    "user-manual-login.feature",
    "choose-which-company-to-represent.feature",
    "create-application-open-new-form-creation-page.feature",
    "add-attachment-to-new-application.feature",
    "open-user-settings-make-and-save-changes.feature"
  );

  @Before(order = 20_000)
  public void prepareScenario(Scenario scenario) {
    PENDING_DATA_REQUESTS.clear();
    lastDataActivityAt = 0;
    clearDefaultDownloads();

    String feature = featureName(scenario);
    if (FRESH_AUTH_FEATURES.contains(feature)) {
      clearBrowserAuthenticationState();
    }

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
  public void cleanupScenario(Scenario scenario) {
    PENDING_DATA_REQUESTS.clear();
    lastDataActivityAt = 0;
    disableChromeFetchInterception();

    // Keep a healthy authenticated session for the next scenario. Only purge
    // auth when the failed scenario actually ended on an authentication or
    // represented-company boundary; those states are unsafe to reuse.
    String current = currentUrl();
    if (scenario.isFailed() && (current.contains("/login") || current.contains("/company-selection"))) {
      clearBrowserAuthenticationState();
    }
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
