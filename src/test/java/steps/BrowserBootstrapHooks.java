package steps;

import io.cucumber.java.BeforeAll;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Normalizes browser-driver startup before any scenario opens a WebDriver.
 *
 * RuntimeState historically auto-pinned /usr/local/bin/chromedriver whenever
 * that file existed and always sent ChromeDriver logs to /tmp. Both assumptions
 * are unsafe for developer machines and mixed runner images: a stale/wrong-arch
 * driver dies before a session is created, and /tmp is not a portable Windows
 * path. This preflight intentionally runs once for the whole suite.
 */
public final class BrowserBootstrapHooks {
  private BrowserBootstrapHooks() { }

  @BeforeAll
  public static void normalizeDriverBootstrap() {
    // Force RuntimeState's configuration block to run now, before any @Before
    // hook opens a browser. We can then correct only the process-level driver
    // settings without changing the 11 golden scenario flows.
    try {
      Class.forName("steps.RuntimeState");
    } catch (ClassNotFoundException error) {
      throw new AssertionError("Browser bootstrap could not initialize RuntimeState", error);
    }

    Path logPath = portableDriverLogPath();
    System.setProperty("webdriver.chrome.logfile", logPath.toString());

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
