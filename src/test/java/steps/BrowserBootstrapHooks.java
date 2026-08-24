package steps;

import com.codeborne.selenide.Configuration;
import io.cucumber.java.BeforeAll;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Normalizes browser-driver startup before any scenario opens a WebDriver.
 *
 * The suite used to auto-pin a driver and reuse one persistent Chrome profile
 * across runs. A stale/locked profile can make Chrome exit before creating
 * DevToolsActivePort, which then surfaces as SessionNotCreatedException for
 * every scenario. This preflight runs once and gives Chrome an isolated profile
 * unless CHROME_PROFILE was explicitly supplied by the operator.
 */
public final class BrowserBootstrapHooks {
  private BrowserBootstrapHooks() { }

  @BeforeAll
  public static void normalizeDriverBootstrap() {
    // Force RuntimeState's configuration block to run now, before any @Before
    // hook opens a browser. We can then correct only bootstrap settings without
    // changing the user-visible behavior of the golden scenarios.
    try {
      Class.forName("steps.RuntimeState");
    } catch (ClassNotFoundException error) {
      throw new AssertionError("Browser bootstrap could not initialize RuntimeState", error);
    }

    Path logPath = portableDriverLogPath();
    System.setProperty("webdriver.chrome.logfile", logPath.toString());
    System.out.println("BROWSER_BOOTSTRAP driver_log=" + logPath);

    String browser = env("OHTEST_BROWSER");
    if (browser.isBlank()) browser = "chrome";
    browser = browser.toLowerCase(Locale.ROOT);

    if (browser.contains("firefox")) {
      System.out.println("BROWSER_BOOTSTRAP browser=firefox driver=geckodriver log=" + logPath);
      return;
    }

    if (browser.equals("edge")) {
      System.clearProperty("webdriver.chrome.driver");
      normalizeExplicitDriver("EDGEDRIVER_PATH", "webdriver.edge.driver", "EdgeDriver");
      return;
    }

    System.clearProperty("webdriver.edge.driver");
    normalizeExplicitDriver("CHROMEDRIVER_PATH", "webdriver.chrome.driver", "ChromeDriver");
    isolateChromeProfile();
  }

  /**
   * Rebuild only the suite-supplied Chrome capabilities, replacing the old
   * persistent --user-data-dir with a fresh profile for this JVM run. Chrome's
   * mTLS certificate on Windows/macOS comes from the OS certificate store; the
   * AutoSelectCertificateForUrls preference is preserved below.
   */
  private static void isolateChromeProfile() {
    Capabilities current = Configuration.browserCapabilities;
    if (current == null) return;

    ChromeOptions replacement = new ChromeOptions();
    Object acceptInsecure = current.getCapability("acceptInsecureCerts");
    if (acceptInsecure instanceof Boolean value) replacement.setAcceptInsecureCerts(value);

    Object loggingPrefs = current.getCapability("goog:loggingPrefs");
    if (loggingPrefs != null) replacement.setCapability("goog:loggingPrefs", loggingPrefs);

    Object rawVendor = current.getCapability(ChromeOptions.CAPABILITY);
    Map<?, ?> vendor = rawVendor instanceof Map<?, ?> map ? map : Map.of();

    Object prefs = vendor.get("prefs");
    if (prefs instanceof Map<?, ?> map) {
      Map<String, Object> copiedPrefs = new HashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getKey() != null) copiedPrefs.put(String.valueOf(entry.getKey()), entry.getValue());
      }
      replacement.setExperimentalOption("prefs", copiedPrefs);
    }

    Object excludeSwitches = vendor.get("excludeSwitches");
    if (excludeSwitches instanceof List<?> list) {
      replacement.setExperimentalOption("excludeSwitches", new ArrayList<>(list));
    }

    Object binary = vendor.get("binary");
    if (binary instanceof String value && !value.isBlank()) replacement.setBinary(value);

    Object rawArgs = vendor.get("args");
    if (rawArgs instanceof List<?> list) {
      List<String> preserved = new ArrayList<>();
      for (Object raw : list) {
        if (!(raw instanceof String arg)) continue;
        if (arg.startsWith("--user-data-dir=")) continue;
        preserved.add(arg);
      }
      if (!preserved.isEmpty()) replacement.addArguments(preserved);
    }

    Path profile = chromeProfilePath();
    replacement.addArguments("--user-data-dir=" + profile);
    Configuration.browserCapabilities = replacement;

    String source = env("CHROME_PROFILE").isBlank() ? "isolated-temp" : "explicit";
    System.out.println("BROWSER_BOOTSTRAP chrome_profile_source=" + source + " path=" + profile);
  }

  private static Path chromeProfilePath() {
    String explicit = env("CHROME_PROFILE");
    try {
      if (!explicit.isBlank()) {
        Path profile = Path.of(explicit).toAbsolutePath().normalize();
        Files.createDirectories(profile);
        return profile;
      }

      String temp = System.getProperty("java.io.tmpdir", "").trim();
      Path root = temp.isBlank()
        ? Path.of("build", "chrome-profiles").toAbsolutePath().normalize()
        : Path.of(temp).toAbsolutePath().normalize();
      Files.createDirectories(root);
      return Files.createTempDirectory(root, "auto13-chrome-profile-").toAbsolutePath().normalize();
    } catch (IOException error) {
      throw new AssertionError("Could not prepare an isolated Chrome profile", error);
    }
  }

  private static void normalizeExplicitDriver(String envName, String propertyName, String displayName) {
    String explicit = env(envName);
    String configured = System.getProperty(propertyName, "").trim();

    if (explicit.isBlank()) {
      // RuntimeState used to discover /usr/local/bin/chromedriver on its own.
      // Clear any such implicit pin and let Selenium Manager resolve the driver
      // that matches the actually installed browser. An operator can still pin
      // an exact binary by setting CHROMEDRIVER_PATH/EDGEDRIVER_PATH explicitly.
      if (!configured.isBlank()) {
        System.out.println("BROWSER_BOOTSTRAP clearing implicit " + displayName
          + " pin; Selenium Manager will resolve the installed browser");
      }
      System.clearProperty(propertyName);
      System.out.println("BROWSER_BOOTSTRAP driver=" + displayName + " source=selenium-manager");
      return;
    }

    Path driver = Path.of(explicit).toAbsolutePath().normalize();
    if (!Files.isRegularFile(driver)) {
      throw new AssertionError(envName + " does not point to a regular file: " + driver);
    }
    if (!isWindows() && !Files.isExecutable(driver)) {
      throw new AssertionError(envName + " is not executable: " + driver);
    }

    verifyDriverExecutable(driver, displayName);
    System.setProperty(propertyName, driver.toString());
    System.out.println("BROWSER_BOOTSTRAP driver=" + displayName + " source=explicit path=" + driver);
  }

  private static void verifyDriverExecutable(Path driver, String displayName) {
    Process process = null;
    try {
      process = new ProcessBuilder(driver.toString(), "--version")
        .redirectErrorStream(true)
        .start();
      boolean finished = process.waitFor(5, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new AssertionError(displayName + " --version did not exit within 5 seconds: " + driver);
      }
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
      if (process.exitValue() != 0) {
        throw new AssertionError(displayName + " binary exited with code " + process.exitValue()
          + " before Selenium startup: " + sanitize(output));
      }
      System.out.println("BROWSER_BOOTSTRAP " + displayName + " version=" + sanitize(output));
    } catch (IOException error) {
      throw new AssertionError(displayName + " binary could not be executed: " + driver
        + " (" + error.getClass().getSimpleName() + ")", error);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new AssertionError(displayName + " preflight was interrupted", error);
    } finally {
      if (process != null && process.isAlive()) process.destroyForcibly();
    }
  }

  private static Path portableDriverLogPath() {
    String temp = System.getProperty("java.io.tmpdir", "").trim();
    Path directory = temp.isBlank()
      ? Path.of("build", "driver-logs").toAbsolutePath().normalize()
      : Path.of(temp).toAbsolutePath().normalize();
    try {
      Files.createDirectories(directory);
    } catch (IOException error) {
      throw new AssertionError("Could not create ChromeDriver log directory: " + directory, error);
    }
    return directory.resolve("auto13-chromedriver.log");
  }

  private static String env(String name) {
    String value = System.getenv(name);
    return value == null ? "" : value.trim();
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  private static String sanitize(String value) {
    if (value == null) return "";
    String oneLine = value.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").trim();
    return oneLine.length() <= 500 ? oneLine : oneLine.substring(0, 500) + "...";
  }
}
