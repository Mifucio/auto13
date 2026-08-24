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
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.logging.Level;

import static com.codeborne.selenide.Selenide.*;
import static com.codeborne.selenide.Selectors.*;
import static com.codeborne.selenide.Condition.*;

public final class RuntimeState {

  // ── Target origins (used by the mTLS auto-select rule and admin nav) ──
  static final String BASE_URL = "https://eservicesdev.sets.lv";
  static final String ADMIN_BASE_URL = "https://eservicesdevint.sets.lv";

  // ── Selenide Configuration ─────────────────────────────────────
  static {
    SelenideLogger.addListener("AllureSelenide", new AllureSelenide()
      .screenshots(true)
      .savePageSource(true));
    // HEADED=true (or HEADED=1) shows the browser window; default is headless.
    String headed = System.getenv("HEADED");
    Configuration.headless = !("true".equalsIgnoreCase(headed) || "1".equals(headed));
    Configuration.browserSize = "1920x1080";
    // Relative open("...") calls must resolve against the target site. Without
    // baseUrl, Selenide opens http://localhost:8080 and every navigation fails.
    if (BASE_URL != null && !BASE_URL.isEmpty()) {
      Configuration.baseUrl = BASE_URL;
    }
    // The stage Angular surfaces can legitimately keep their loader visible for
    // close to one minute. Assertions must wait for the rendered business
    // surface instead of archiving a spinner as a product failure.
    Configuration.timeout = configuredLong("elementTimeoutMs", "TEST_ELEMENT_TIMEOUT_MS", 70000);
    Configuration.pageLoadTimeout = configuredLong("pageLoadTimeoutMs", "TEST_PAGE_LOAD_TIMEOUT_MS", 90000);
    Configuration.pageLoadStrategy = "eager";
    Configuration.holdBrowserOpen = false;
    Configuration.screenshots = true;
    Configuration.savePageSource = true;
    Configuration.downloadsFolder = Path.of("build", "downloads").toAbsolutePath().toString();

    // ── Browser: Chromium (Chrome) by default, Firefox only on explicit opt-in ──
    // mTLS on Chromium is handled via AutoSelectCertificateForUrls against the
    // OS certificate store (Windows: CurrentUser\My; Linux/macOS: NSS/chrome
    // profile). Firefox is kept as a legacy fallback (profile NSS DB +
    // security.default_personal_cert) for environments that need it.
    String browser = System.getenv("OHTEST_BROWSER");
    String targetBrowser = (browser == null || browser.isBlank()) ? "chrome" : browser.toLowerCase(Locale.ROOT);
    if (targetBrowser.contains("firefox")) {
      Configuration.browser = "firefox";
      org.openqa.selenium.firefox.FirefoxOptions ffOptions = new org.openqa.selenium.firefox.FirefoxOptions();
      ffOptions.setAcceptInsecureCerts(true);
      // Point geckodriver at the portable Firefox binary.  The env var
      // OHTEST_FIREFOX_BINARY (or the locator default) lets CI and
      // developer workstations set the path without hardcoding it here.
      String ffBinary = System.getenv("OHTEST_FIREFOX_BINARY");
      if (ffBinary != null && !ffBinary.isBlank()) {
        ffOptions.setBinary(ffBinary);
        System.out.println("  🦊 Firefox binary: " + ffBinary);
      }
      // BiDi (network mocks) requires the WebSocket connection.
      ffOptions.setCapability("webSocketUrl", true);
      ffOptions.addPreference("security.default_personal_cert", "Select Automatically");
      ffOptions.addPreference("browser.download.folderList", 2);
      ffOptions.addPreference("browser.download.dir", Configuration.downloadsFolder);
      ffOptions.addPreference("browser.download.useDownloadDir", true);
      ffOptions.addPreference("browser.download.manager.showWhenStarting", false);
      ffOptions.addPreference("browser.helperApps.neverAsk.saveToDisk", "application/pdf,application/octet-stream,application/vnd.etsi.asic-e+zip,application/zip");
      String ffProfile = System.getenv("FIREFOX_PROFILE");
      if (ffProfile != null && !ffProfile.isEmpty()) {
        ffOptions.addArguments("--profile", ffProfile);
      }
      Configuration.browserCapabilities = ffOptions;
      System.out.println("  🦊 Browser: Firefox (mTLS profile " + (ffProfile != null ? ffProfile : "default") + ")");
    } else {
      // Chrome and Edge are both Chromium: the same capabilities (mTLS
      // AutoSelectCertificateForUrls, persistent profile, downloads) apply.
      Configuration.browser = ("edge".equals(targetBrowser)) ? "edge" : "chrome";
      System.out.println("  🌐 Browser: " + (Configuration.browser.equals("edge") ? "Microsoft Edge (Chromium)" : "Google Chrome (Chromium)"));

    // ChromeDriver diagnostics: verbose log to /tmp/chromedriver.log (also
    // emitted to the test stdout) so "Chrome instance exited" failures are
    // debuggable in the suite-runner image.
    System.setProperty("webdriver.chrome.verboseLogging", "true");
    System.setProperty("webdriver.chrome.logfile", "/tmp/chromedriver.log");

    // Pin the exact ChromeDriver: prefer an explicitly provided binary
    // (CHROMEDRIVER_PATH env, or /usr/local/bin/chromedriver in the suite
    // image) over Selenium Manager's auto-download, which may pick a
    // DIFFERENT patch version than the installed Chrome.
    String driverPath = System.getenv("CHROMEDRIVER_PATH");
    if ((driverPath == null || driverPath.isEmpty()) && new java.io.File("/usr/local/bin/chromedriver").exists()) {
      driverPath = "/usr/local/bin/chromedriver";
    }
    if ("edge".equals(Configuration.browser)) {
      if (driverPath == null || driverPath.isEmpty()) driverPath = System.getenv("EDGEDRIVER_PATH");
      if (driverPath != null && !driverPath.isEmpty()) {
        System.setProperty("webdriver.edge.driver", driverPath);
        System.out.println("  🔧 EdgeDriver (msedgedriver): " + driverPath);
      }
    } else if (driverPath != null && !driverPath.isEmpty()) {
      System.setProperty("webdriver.chrome.driver", driverPath);
      System.out.println("  🔧 ChromeDriver: " + driverPath);
    }

    // Chrome (headless) listens on 127.0.0.1; the JVM may resolve "localhost"
    // to ::1 first and fail to connect the DevTools WebSocket. Force IPv4 for
    // the driver connection (best-effort; JVM reads it at init).
    System.setProperty("java.net.preferIPv4Stack", "true");

    // ── Client Certificate (mTLS) ────────────────────────────
    ChromeOptions chromeOptions = new ChromeOptions();
    LoggingPreferences logging = new LoggingPreferences();
    logging.enable(LogType.PERFORMANCE, Level.ALL);
    logging.enable(LogType.BROWSER, Level.ALL);
    chromeOptions.setCapability("goog:loggingPrefs", logging);
    chromeOptions.setAcceptInsecureCerts(true);

    // Постоянный профиль Chrome: клиентский сертификат выбирается ОДИН раз
    // (в первый запуск), Chrome запоминает решение в профиле и больше не
    // показывает окно выбора. Без этого Selenide создаёт временный профиль
    // на каждый запуск, и подтверждение требуется снова.
    String userDataDir = System.getenv("CHROME_PROFILE");
    if (userDataDir == null || userDataDir.isEmpty()) {
      userDataDir = System.getProperty("user.home") + "/.test-chrome-profile";
    }
    chromeOptions.addArguments("--user-data-dir=" + userDataDir);

    // Container/K8s: Chrome's sandbox needs privileges that pods lack; the
    // entrypoint sets CHROME_NO_SANDBOX=true in the suite-runner image.
    if (Boolean.parseBoolean(System.getenv("CHROME_NO_SANDBOX"))) {
      chromeOptions.addArguments("--no-sandbox", "--disable-dev-shm-usage");
    }

    // mTLS: нативный автовыбор клиентского сертификата.
    //   Windows/macOS: сертификат импортирован в системное хранилище, Chrome
    //     молча выбирает его по этому правилу (без модального окна).
    //   Linux: сертификат импортирован в NSS DB профиля (~/.pki/nssdb),
    //     Chrome берёт его отсюда и применяет то же правило.
    // Паттерн "*" = любой сайт, пустой filter = брать ПЕРВЫЙ доступный
    // клиентский сертификат без показа окна выбора.
    // Прокси-подход (BrowserUp/LittleProxy) НЕ используется: он не умеет
    // предъявлять клиентский сертификат для исходящих mTLS-соединений.
    String autoCertPattern = "{\"pattern\":\"*\",\"filter\":{}}";
    Map<String, Object> prefs = new HashMap<>();
    prefs.put("AutoSelectCertificateForUrls", List.of(autoCertPattern));
    chromeOptions.setExperimentalOption("prefs", prefs);
    System.out.println("  🔐 AutoSelectCertificateForUrls: " + autoCertPattern);

    String clientCert = System.getenv("CLIENT_CERT_PATH");
    String clientCertKey = System.getenv("CLIENT_CERT_KEY_PATH");
    String clientCertPassword = System.getenv("CLIENT_CERT_PASSWORD");
    if ((clientCert == null || clientCert.isEmpty()) || (clientCertPassword == null || clientCertPassword.isEmpty())) {
      // Fall back to credentials.local.properties (mtls.cert / mtls.password) so a
      // single config file drives both credentials and mTLS on a clean machine.
      String cert = localProperty("mtls.cert");
      String pass = localProperty("mtls.password");
      if (clientCert == null || clientCert.isEmpty()) clientCert = cert;
      if (clientCertPassword == null || clientCertPassword.isEmpty()) clientCertPassword = pass;
    }
    if (clientCert != null && !clientCert.isEmpty()) {
      if (clientCertKey != null && !clientCertKey.isEmpty()) {
        // PEM: подходит для Linux/NSS (файлы уже в базе) и как подсказка.
        System.out.println("  🔐 Client certificate (PEM): " + clientCert);
      } else if (clientCertPassword != null && !clientCertPassword.isEmpty()) {
        // PFX/P12: JVM keyStore для Java-клиентов (Chrome сам берёт из ОС/NSS).
        System.setProperty("javax.net.ssl.keyStore", clientCert);
        System.setProperty("javax.net.ssl.keyStorePassword", clientCertPassword);
        System.setProperty("javax.net.ssl.keyStoreType", "PKCS12");
        System.out.println("  🔐 Client certificate (PFX): " + clientCert);
      } else {
        // PKCS#11 / system store
        chromeOptions.addArguments("--enable-features=PlatformPKCS11");
      }
    }
    Configuration.browserCapabilities = chromeOptions;
    }
  }

  // Read a key from credentials.local.properties (next to the suite) so a single
  // config file can carry credentials and mTLS on a clean machine.
  private static String localProperty(String key) {
    try {
      String file = System.getenv("LOCAL_CREDENTIALS_FILE");
      Path path = (file != null && !file.isBlank()) ? Path.of(file)
        : Path.of(System.getProperty("user.dir"), "credentials.local.properties");
      if (!Files.isRegularFile(path)) return "";
      for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith("#")) continue;
        int eq = line.indexOf('=');
        if (eq <= 0) continue;
        if (key.equals(line.substring(0, eq).trim())) return line.substring(eq + 1).trim();
      }
    } catch (Exception ignored) { }
    return "";
  }

  // ── Console/performance/network/mock state ─────────────────────
  static final List<String> CONSOLE_LOGS = new ArrayList<>();
  static final List<String> PERFORMANCE_RESULTS = new ArrayList<>();
  static final Map<String, PendingRequest> PENDING_DATA_REQUESTS = new HashMap<>();
  static final Map<String, List<String>> CHECKPOINTS_BY_SCENARIO = Map.ofEntries(
    Map.entry("add-attachment-to-new-application.feature|[admin] \"Add attachment to new application\" for <company>, <caForm>|Then|attachment_added", List.of("add-attachment-to-new-application.admin-add-attachment-to-new-application-for-company-caform.attachment-added")),
    Map.entry("assign-application-to-internal-user.feature|[admin] \"Assign Application to internal user\"|Then|assignment_saved", List.of("assign-application-to-internal-user.admin-assign-application-to-internal-user.assignment-saved")),
    Map.entry("attach-a-file-in-attachments-tab.feature|[admin] \"Attach a file in Attachments tab\"|Then|attachment_added", List.of("attach-a-file-in-attachments-tab.admin-attach-a-file-in-attachments-tab.attachment-added")),
    Map.entry("choose-which-company-to-represent.feature|Choose which company to represent|Then|company_context_applied", List.of("choose-which-company-to-represent.choose-which-company-to-represent.company-context-applied")),
    Map.entry("create-application-open-new-form-creation-page.feature|[admin] \"Create application → open new form creation page\" for <company>, <caForm>|Then|form_creation_page", List.of("create-application-open-new-form-creation-page.admin-create-application-open-new-form-creation-page-for-company-caform.form-creation-page")),
    Map.entry("download-application.feature|[admin] \"Download application\"|Then|file_downloaded", List.of("download-application.admin-download-application.file-downloaded")),
    Map.entry("download-attachment-from-application.feature|[admin] \"Download attachment from application\" for <company>, <caForm>|Then|file_downloaded", List.of("download-attachment-from-application.admin-download-attachment-from-application-for-company-caform.file-downloaded")),
    Map.entry("download-saved-application-check-if-printout-is-generated.feature|[admin] \"Download saved application - check if printout is generated\" for <company>, <caForm>|Then|printout_generated", List.of("download-saved-application-check-if-printout-is-generated.admin-download-saved-application-check-if-printout-is-generated-for-company-caform.printout-generated")),
    Map.entry("edit-external-role-int.feature|[admin] \"Edit external role\"|Then|role_saved_confirmation", List.of("edit-external-role.admin-edit-external-role.role-saved-confirmation")),
    Map.entry("edit-internal-role.feature|[admin] \"Edit internal role\"|Then|role_saved_confirmation", List.of("edit-internal-role.admin-edit-internal-role.role-saved-confirmation")),
    Map.entry("edit-internal-user.feature|[admin] \"Edit internal user\"|Then|user_saved_confirmation", List.of("edit-internal-user.admin-edit-internal-user.user-saved-confirmation")),
    Map.entry("edit-person.feature|[admin] \"Edit Person\"|Then|person_saved_confirmation", List.of("edit-person.admin-edit-person.person-saved-confirmation")),
    Map.entry("filter-persons.feature|[admin] \"Filter Persons\"|Then|filtered_persons_list", List.of("filter-persons.admin-filter-persons.filtered-persons-list")),
    Map.entry("initiate-signature-process-view-signees.feature|[admin] \"Initiate signature process, view signees\" for <company>, <caForm>|Then|signees_list_visible", List.of("initiate-signature-process-view-signees.admin-initiate-signature-process-view-signees-for-company-caform.signees-list-visible")),
    Map.entry("open-home-page.feature|[admin] \"Open Home page\"|Then|home_visible", List.of("open-home-page.admin-open-home-page.home-visible")),
    Map.entry("open-home-page.feature|[admin] \"Open Home page\"|And|upcoming_events_widget", List.of("open-home-page.admin-open-home-page.upcoming-events-widget")),
    Map.entry("open-home-page-2.feature|Open Home page|Then|home_visible", List.of("open-home-page.open-home-page.home-visible")),
    Map.entry("open-home-page-2.feature|Open Home page|And|upcoming_events_widget", List.of("open-home-page.open-home-page.upcoming-events-widget")),
    Map.entry("open-persons-list.feature|[admin] \"Open Persons list\"|Then|persons_list_visible", List.of("open-persons-list.admin-open-persons-list.persons-list-visible")),
    Map.entry("open-roles-external-roles-list.feature|[admin] \"Open Roles → External roles list\"|Then|external_roles_list_visible", List.of("open-roles-external-roles-list.admin-open-roles-external-roles-list.external-roles-list-visible")),
    Map.entry("open-roles-internal-roles-list.feature|[admin] \"Open Roles → Internal roles list\"|Then|internal_roles_list_visible", List.of("open-roles-internal-roles-list.admin-open-roles-internal-roles-list.internal-roles-list-visible")),
    Map.entry("open-user-settings-make-and-save-changes.feature|Open User Settings, make and save changes|Then|settings_saved_confirmation", List.of("open-user-settings-make-and-save-changes.open-user-settings-make-and-save-changes.settings-saved-confirmation")),
    Map.entry("open-user-settings-make-and-save-changes.feature|Open User Settings, make and save changes|Then|settings_editor_no_save_boundary", List.of("open-user-settings-make-and-save-changes.open-user-settings-make-and-save-changes.settings-editor-no-save-boundary")),
    Map.entry("open-users-external-users-list.feature|[admin] \"Open Users → External users list\"|Then|external_users_list_visible", List.of("open-users-external-users-list.admin-open-users-external-users-list.external-users-list-visible")),
    Map.entry("open-users-internal-users-list.feature|[admin] \"Open Users → Internal users list\"|Then|internal_users_list_visible", List.of("open-users-internal-users-list.admin-open-users-internal-users-list.internal-users-list-visible")),
    Map.entry("reject-application-add-comments-check-if-status-changes-to-invalid.feature|[admin] \"Reject Application, add comments, check if status changes to Invalid\"|Then|status_invalid", List.of("reject-application-add-comments-check-if-status-changes-to-invalid.admin-reject-application-add-comments-check-if-status-changes-to-invalid.status-invalid")),
    Map.entry("save-new-application.feature|[admin] \"Save new application\" for <company>, <caForm>|Then|application_saved", List.of("save-new-application.admin-save-new-application-for-company-caform.application-saved")),
    Map.entry("save-new-application.feature|[admin] \"Save new application\" for <company>, <caForm>|And|status_draft", List.of("save-new-application.admin-save-new-application-for-company-caform.status-draft")),
    Map.entry("search-corporate-actions-list.feature|[admin] \"Search Corporate actions list\"|Then|application_search_results", List.of("search-corporate-actions-list.admin-search-corporate-actions-list.application-search-results")),
    Map.entry("search-external-user.feature|[admin] \"Search External user\"|Then|search_results_visible", List.of("search-external-user.admin-search-external-user.search-results-visible")),
    Map.entry("search-persons.feature|[admin] \"Search Persons\"|Then|person_search_results", List.of("search-persons.admin-search-persons.person-search-results")),
    Map.entry("sign-application-via-dokobit.feature|[admin] \"Sign application via Dokobit\" for <company>, <caForm>|Then|application_signed", List.of("sign-application-via-dokobit.admin-sign-application-via-dokobit-for-company-caform.application-signed")),
    Map.entry("sign-application-via-dokobit.feature|[admin] \"Sign application via Dokobit\" for <company>, <caForm>|And|status_changed", List.of("sign-application-via-dokobit.admin-sign-application-via-dokobit-for-company-caform.status-changed")),
    Map.entry("user-login-via-dokobit-smart-id-or-mobile-id.feature|User login via Dokobit (Smart-ID or Mobile ID)|Then|dashboard_visible", List.of("user-login-via-dokobit-smart-id-or-mobile-id.user-login-via-dokobit-smart-id-or-mobile-id.dashboard-visible")),
    Map.entry("user-manual-login.feature|[admin] \"User manual login\"|Then|home_visible", List.of("user-manual-login.admin-user-manual-login.home-visible")),
    Map.entry("view-attachments-tab.feature|[admin] \"View Attachments tab\"|Then|attachments_list_visible", List.of("view-attachments-tab.admin-view-attachments-tab.attachments-list-visible")),
    Map.entry("view-attachments-tab-2.feature|[admin] \"View Attachments tab\"|Then|attachments_list_visible", List.of("view-attachments-tab-2.admin-view-attachments-tab.attachments-list-visible")),
    Map.entry("view-attachments-tab-2.feature|[admin] \"View Attachments tab\" for <company>, <caForm>|Then|attachments_list_visible", List.of("view-attachments-tab.admin-view-attachments-tab-for-company-caform.attachments-list-visible")),
    Map.entry("view-corporate-actions-application-list-browse-different-tabs.feature|[admin] \"View Corporate actions Application list, browse different tabs\"|Then|application_list_visible", List.of("view-corporate-actions-application-list-browse-different-tabs.admin-view-corporate-actions-application-list-browse-different-tabs.application-list-visible")),
    Map.entry("view-history-tab.feature|[admin] \"View History tab\"|Then|history_entries_visible", List.of("view-history-tab.admin-view-history-tab.history-entries-visible")),
    Map.entry("view-history-tab-2.feature|[admin] \"View History tab\"|Then|history_entries_visible", List.of("view-history-tab-2.admin-view-history-tab.history-entries-visible")),
    Map.entry("view-history-tab-2.feature|[admin] \"View History tab\" for <company>, <caForm>|Then|history_entries_visible", List.of("view-history-tab.admin-view-history-tab-for-company-caform.history-entries-visible")),
    Map.entry("view-signatures-tab.feature|[admin] \"View Signatures tab\"|Then|signatures_visible", List.of("view-signatures-tab.admin-view-signatures-tab.signatures-visible")),
    Map.entry("view-single-application.feature|[admin] \"View single application\"|Then|application_details_visible", List.of("view-single-application.admin-view-single-application.application-details-visible")),
    Map.entry("view-upcoming-events-in-home-page.feature|[admin] \"View upcoming events in home page\"|Then|upcoming_events_visible", List.of("view-upcoming-events-in-home-page.admin-view-upcoming-events-in-home-page.upcoming-events-visible"))
  );
  static final Map<String, Integer> SCENARIO_CHECKPOINT_OCCURRENCES = new HashMap<>();
  // BiDi/CDP network mock state: an intercept stays active in the browser
  // session until explicitly removed, so each scenario tears it down.
  static volatile boolean mockActive = false;
  static org.openqa.selenium.bidi.module.Network mockNetwork;
  static String mockInterceptId;
  static long stepStartedAt;
  static long scenarioStartedAt;
  static long suiteStartedAt;
  static String currentStep = "unknown step";
  static String currentFeatureFile = "unknown.feature";
  static String currentScenarioName = "unknown scenario";
  static long lastDataActivityAt;
  static final long HANG_TIMEOUT_MS = configuredLong("hangTimeoutMs", "TEST_HANG_TIMEOUT_MS", 45000);
  static final long SLOW_STEP_MS = configuredLong("elementSlowThresholdMs", "TEST_ELEMENT_SLOW_MS", 2000);
  static final long EXTERNAL_SLOW_MS = configuredLong("externalDataSlowThresholdMs", "TEST_EXTERNAL_DATA_SLOW_MS", 3000);
  static final long EXTERNAL_TIMEOUT_MS = configuredLong("externalDataTimeoutMs", "TEST_EXTERNAL_DATA_TIMEOUT_MS", 15000);
  static final long NETWORK_QUIET_MS = configuredLong("networkQuietMs", "TEST_NETWORK_QUIET_MS", 500);

  // ── Small utilities ────────────────────────────────────────────
  static String jsonEscape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
  }

  static long configuredLong(String key, String environmentKey, long fallback) {
    String environmentValue = System.getenv(environmentKey);
    if (environmentValue != null && !environmentValue.isBlank()) {
      try { return Long.parseLong(environmentValue); } catch (NumberFormatException ignored) { }
    }
    try {
      Path configPath = Path.of("performance.config.json");
      if (Files.exists(configPath)) {
        JsonObject config = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
        if (config.has(key) && config.get(key).isJsonPrimitive()) return config.get(key).getAsLong();
      }
    } catch (Exception e) {
      System.err.println("  ⚠️  Performance config read failed: " + e.getMessage());
    }
    return fallback;
  }

  static void writePerformanceReport() {
    try {
      Files.createDirectories(Path.of("reports"));
      long totalDurationMs = suiteStartedAt > 0 ? System.currentTimeMillis() - suiteStartedAt : 0;
      String report = "{\"totalDurationMs\":" + totalDurationMs
        + ",\"results\":[" + String.join(",", PERFORMANCE_RESULTS) + "]}\n";
      Files.writeString(Path.of("reports", "performance-results.json"), report);
      Allure.addAttachment("Performance results", "application/json", report);
    } catch (Exception e) {
      System.err.println("  ⚠️  Performance report failed: " + e.getMessage());
    }
  }

  // ── Observability (built-in) ──────────────────────────────────────
  // Writes one NDJSON line per step/scenario to
  //   $OBS_DIR/events.ndjson   (OBS_DIR default: ./observability)
  // and, when the OpenTelemetry Java agent is attached (OHTEST_OBSERVABILITY
  // = otlp), exports the same events as OTLP spans. The entrypoint mirrors
  // the file into the pod's emptyDir for the artifact uploader.
  static String obsDir() {
    String dir = System.getenv("OBS_DIR");
    return dir != null && !dir.isBlank() ? dir : "observability";
  }
  static void writeObservabilityEvent(String kind, String name, long durationMs, boolean failed, String error) {
    try {
      String runId = System.getenv().getOrDefault("OHTEST_RUN_ID", "local");
      String tenantId = System.getenv().getOrDefault("OHTEST_TENANT_ID", "local");
      String line = "{\"ts\":" + System.currentTimeMillis()
        + ",\"runId\":\"" + jsonEscape(runId)
        + "\",\"tenantId\":\"" + jsonEscape(tenantId)
        + "\",\"kind\":\"" + kind
        + "\",\"name\":\"" + jsonEscape(name)
        + "\",\"feature\":\"" + jsonEscape(currentFeatureFile)
        + "\",\"durationMs\":" + durationMs
        + ",\"failed\":" + failed
        + (error != null ? ",\"error\":\"" + jsonEscape(error) + "\"" : "")
        + "}\n";
      Files.createDirectories(Path.of(obsDir()));
      Files.writeString(Path.of(obsDir(), "events.ndjson"), line,
        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    } catch (Exception e) {
      // Observability must never break the test run.
      System.err.println("  ⚠️  observability event failed: " + e.getMessage());
    }
  }

  static String getConsoleLogs() {
    try {
      Object logs = executeJavaScript("return window.__testConsole;");
      if (logs instanceof List) {
        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) logs;
        return String.join("\\n", list);
      }
    } catch (Exception e) {
      // JavaScript execution may fail on some pages
    }
    return CONSOLE_LOGS.stream().collect(Collectors.joining("\\n"));
  }

  static java.util.List<String> getDriverConsoleLogs() {
    List<String> result = new ArrayList<>();
    try {
      WebDriver driver = WebDriverRunner.getWebDriver();
      if (driver == null) return result;
      for (LogEntry entry : driver.manage().logs().get(LogType.BROWSER)) {
        result.add("[" + entry.getLevel() + "] " + entry.getMessage());
      }
    } catch (Exception e) {
      // Browser logs not available (e.g., remote driver)
    }
    return result;
  }

  static final class PendingRequest {
    final String url;
    final long startedAt;

    PendingRequest(String url, long startedAt) {
      this.url = url;
      this.startedAt = startedAt;
    }
  }

  static void captureScenarioCheckpoint(String annotation, String pattern) {
    String key = currentFeatureFile + "|" + currentScenarioName + "|" + annotation + "|" + pattern;
    List<String> checkpointIds = CHECKPOINTS_BY_SCENARIO.get(key);
    if (checkpointIds == null || checkpointIds.isEmpty()) {
      throw new AssertionError("Missing generated checkpoint mapping for " + key);
    }
    int occurrence = SCENARIO_CHECKPOINT_OCCURRENCES.getOrDefault(key, 0);
    if (occurrence >= checkpointIds.size()) {
      throw new AssertionError("Checkpoint occurrence exceeds generated mapping for " + key);
    }
    SCENARIO_CHECKPOINT_OCCURRENCES.put(key, occurrence + 1);
    CheckpointCapture.capture(checkpointIds.get(occurrence));
  }

}
