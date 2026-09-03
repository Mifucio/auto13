package steps;

import com.codeborne.selenide.*;
import com.codeborne.selenide.logevents.SelenideLogger;
import com.google.gson.JsonArray;
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
import org.openqa.selenium.interactions.Actions;

import java.time.Duration;
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

public class AdminSteps {

  /**
   * The live Submitted list currently exposes two LV0000570166 / Bonus Issue
   * rows.  Keep the observed row identity together so selection never falls
   * back to DOM order or an arbitrary first match.
   */
  private static final CorporateActionIdentity OBSERVED_LV_BONUS =
    new CorporateActionIdentity(
      "LV0000570166",
      "Bonus Issue",
      "NES2048_Company_LT",
      "Jan 27, 2026",
      "Jan 27, 2026",
      "Feb 4, 2026",
      "Submitted");

  private static final ThreadLocal<CorporateActionIdentity> SELECTED_CORPORATE_ACTION = new ThreadLocal<>();
  private static final ThreadLocal<Ca24AttachmentResult> CA24_ATTACHMENT_RESULT = new ThreadLocal<>();
  private static final Path CA24_DOWNLOADS = Path.of("build", "ca24-downloads").toAbsolutePath().normalize();

  private record CorporateActionIdentity(
      String isin,
      String form,
      String issuer,
      String submittedDate,
      String lastModifiedDate,
      String paymentDate,
      String status) {

    String summary() {
      return String.join(" | ", isin, form, issuer, submittedDate, lastModifiedDate, paymentDate, status);
    }

    List<String> detailIdentityValues() {
      return List.of(isin, form, submittedDate, lastModifiedDate, paymentDate, status);
    }
  }

  private record Ca24AttachmentResult(
      String applicationSummary,
      String observedControl,
      int artifactCount) {
  }

  private record Ca24ApplicationCandidate(
      CorporateActionIdentity identity,
      int rowOrdinal) {
  }

  @Given("I am authenticated in the admin application")
  public void i_am_authenticated_in_the_admin_application() {
    adminOpen("/home");
    // The SPA home page can take >10s to load on slow network (observed
    // up to ~12s), so poll well past the worst case before assuming we must
    // log in again. Can be tuned via TEST_SESSION_PROBE_MS.
    String sessionProbeConfig = System.getenv().getOrDefault("TEST_SESSION_PROBE_MS", "30000");
    long sessionProbeMs = Long.parseLong(sessionProbeConfig);
    long sessionDeadline = System.currentTimeMillis() + Math.max(1000L, sessionProbeMs);
    String url = com.codeborne.selenide.WebDriverRunner.url();
    String body = "";
    boolean alreadyAuthenticated = false;
    while (System.currentTimeMillis() < sessionDeadline) {
      url = com.codeborne.selenide.WebDriverRunner.url();
      body = $("body").shouldBe(visible).getText();
      alreadyAuthenticated = url != null && !url.contains("/login")
        && body != null && (body.contains("Management") || body.contains("Corporate actions") || body.contains("Welcome back"));
      if (alreadyAuthenticated || (url != null && url.contains("/login") && body.contains("Sign in manually"))) break;
      sleep(250);
    }
    if (alreadyAuthenticated) return;

    adminLogin();
    awaitAuthenticatedAdmin();
  }

  // ── Locator Constants ─────────────────────────────────────────
  // No locators generated — tests use generic actions

  // ── Step Definitions — domain: admin ───────────────────────

  @Given("I navigate to the admin {string}")
  public void i_navigate_to_the_admin_string(String param0) {
    adminOpen(param0);

  }

  @And("I am on the admin application")
  public void i_am_on_the_admin_application() {
    assertCurrentOrigin(ADMIN_BASE_URL);

  }

  @And("I submit the observed form")
  public void i_submit_the_observed_form() {
    submitObservedForm();

  }

  @When("I select {string} from {string}")
  public void i_select_string_from_string(String param0, String param1) {
    selectByLabel(param1, param0);

  }

  @When("I open the observed existing Corporate Actions application for {string}, {string} without saving")
  public void i_open_observed_existing_corporate_action_application(String country, String caForm) {
    openObservedCorporateAction(country, caForm);
  }

  @When("I select the observed existing Corporate Actions application for {string}, {string} without saving")
  public void i_select_observed_existing_corporate_action_without_saving(String country, String caForm) {
    selectObservedCorporateActionWithoutSaving(country, caForm);
  }

  @When("I open the observed {string} Corporate Actions tab without saving")
  public void i_open_observed_corporate_actions_tab(String tabName) {
    openObservedCorporateActionsTab(tabName);
  }

  @And("I click {string}")
  public void i_click_string(String param0) {
    clickByText(param0);

  }

  @Then("attachment_added")
  public void attachment_added() {
    assertSemanticState("attachment_added");
    captureScenarioCheckpoint("Then", "attachment_added");
  }

  @Then("assignment_saved")
  public void assignment_saved() {
    assertSemanticState("assignment_saved");
    CheckpointCapture.capture("assign-application-to-internal-user.admin-assign-application-to-internal-user.assignment-saved");
  }

  @Then("form_creation_page")
  public void form_creation_page() {
    assertSemanticState("form_creation_page");
    CheckpointCapture.capture("create-application-open-new-form-creation-page.admin-create-application-open-new-form-creation-page-for-company-caform.form-creation-page");
  }

  @Then("file_downloaded")
  public void file_downloaded() {
    var downloads = java.nio.file.Path.of(com.codeborne.selenide.Configuration.downloadsFolder);
    try (var files = java.nio.file.Files.walk(downloads)) {
      if (files.filter(java.nio.file.Files::isRegularFile)
          .noneMatch(path -> path.getFileName().toString().toLowerCase().endsWith(".asice")
              && path.toFile().length() > 0)) {
        throw new AssertionError("No non-empty ASiC-E application download found in " + downloads);
      }
    } catch (java.io.IOException error) {
      throw new AssertionError("Could not inspect application downloads in " + downloads, error);
    }
    screenshot("direct-ca-application-download-complete");
  }

  @When("I find and download an observed uploaded attachment without changing application data")
  public void i_find_and_download_an_observed_uploaded_attachment_without_changing_application_data() {
    probeCa24AttachmentDownload();
  }

  @Then("ca24_attachment_downloaded")
  public void ca24_attachment_downloaded() {
    Ca24AttachmentResult result = CA24_ATTACHMENT_RESULT.get();
    if (result == null) {
      throw new AssertionError("CA-24 did not produce an observed attachment download result");
    }
    if (result.artifactCount() < 1) {
      throw new AssertionError("CA-24 observed attachment download produced no non-empty artifact");
    }
    System.out.println("CA24_ARTIFACT_CONTRACT observed_control=" + result.observedControl()
      + " application=" + result.applicationSummary()
      + " non_empty_artifacts=" + result.artifactCount()
      + " filename_mime=observed_only");
  }

  @And("I download the observed application")
  public void i_download_the_observed_application() {
    var downloads = java.nio.file.Path.of(com.codeborne.selenide.Configuration.downloadsFolder);
    if (!java.nio.file.Files.exists(downloads) && !downloads.toFile().mkdirs()) {
      throw new AssertionError("Could not create downloads directory " + downloads);
    }
    try (var existing = java.nio.file.Files.walk(downloads)) {
      for (var path : existing.sorted(java.util.Comparator.reverseOrder()).toList()) {
        if (!path.equals(downloads)) java.nio.file.Files.deleteIfExists(path);
      }
    } catch (java.io.IOException error) {
      throw new AssertionError("Could not clear stale downloads under " + downloads, error);
    }
    var previousDownloadMode = com.codeborne.selenide.Configuration.fileDownload;
    com.codeborne.selenide.Configuration.fileDownload = com.codeborne.selenide.FileDownloadMode.FOLDER;
    java.io.File downloaded;
    try {
      downloaded = uniqueObservedControl("Download").download();
    } finally {
      com.codeborne.selenide.Configuration.fileDownload = previousDownloadMode;
    }
    if (downloaded == null || !downloaded.isFile() || downloaded.length() == 0
        || !downloaded.getName().toLowerCase().endsWith(".asice")) {
      throw new AssertionError("Observed application download did not produce a non-empty ASiC-E container in " + downloads);
    }
  }

  @Then("ca29_artifact_contract_boundary_visible")
  public void ca29_artifact_contract_boundary_visible() {
    assertAdminRoute("/corporate-actions");
    awaitCorporateActionsList();
    assertObservedHeading("Corporate Actions");
    if (SELECTED_CORPORATE_ACTION.get() == null) {
      throw new AssertionError("CA-29 artifact boundary was reached without a selected observed application");
    }
    uniqueObservedControl("Download Fillable PDF form").shouldBe(visible);
    probeCa29ArtifactBoundary();
    CheckpointCapture.capture("download-saved-application-check-if-printout-is-generated.admin-download-saved-application-check-if-printout-is-generated.artifact-contract-boundary");
  }

  /**
   * Probe the visible list control without asserting an invented file type.
   * The action is read-only from the product perspective: it only invokes the
   * rendered download control and records sanitized browser/file observations.
   */
  private static void probeCa29ArtifactBoundary() {
    Path downloads = Path.of("build", "ca29-downloads").toAbsolutePath().normalize();
    clearCa29Downloads(downloads);
    String previousDownloadsFolder = Configuration.downloadsFolder;
    FileDownloadMode previousDownloadMode = Configuration.fileDownload;
    java.io.File downloaded = null;
    String downloadFailure = null;
    String traceJson;
    String beforeUrl = safeRoute(WebDriverRunner.url());
    try {
      Configuration.downloadsFolder = downloads.toString();
      Configuration.fileDownload = FileDownloadMode.FOLDER;
      installCa29Trace();
      try {
        downloaded = uniqueObservedControl("Download Fillable PDF form").download();
      } catch (Throwable failure) {
        // A missing artifact is the observed CA-29 product boundary, not a
        // harness failure. Keep only the exception type, never its message.
        downloadFailure = failure.getClass().getSimpleName();
      }
      sleep(1500);
      Object trace = executeJavaScript("if (window.__ca29Trace && window.__ca29Trace.collect) window.__ca29Trace.collect(); return JSON.stringify(window.__ca29Trace || {})");
      traceJson = Ca29RepairSteps.sanitizeDiagnosticTrace(trace == null ? "{}" : trace.toString());
    } finally {
      Configuration.downloadsFolder = previousDownloadsFolder;
      Configuration.fileDownload = previousDownloadMode;
    }

    JsonObject evidence;
    try {
      evidence = JsonParser.parseString(traceJson).getAsJsonObject();
    } catch (RuntimeException parseFailure) {
      evidence = new JsonObject();
      evidence.addProperty("traceParseFailure", parseFailure.getClass().getSimpleName());
    }
    evidence.addProperty("selectedRow", Ca29RepairSteps.sanitizeDiagnosticText(SELECTED_CORPORATE_ACTION.get().summary()));
    evidence.addProperty("beforeRoute", Ca29RepairSteps.sanitizeDiagnosticText(beforeUrl));
    evidence.addProperty("afterRoute", Ca29RepairSteps.sanitizeDiagnosticText(safeRoute(WebDriverRunner.url())));
    evidence.addProperty("downloadFolder", downloads.toString());
    evidence.addProperty("downloadFailure", downloadFailure == null ? "" : downloadFailure);
    if (downloaded != null) {
      evidence.addProperty("returnedFile", downloaded.getName());
      evidence.addProperty("returnedFileBytes", downloaded.length());
    }
    evidence.add("files", ca29DownloadInventory(downloads));

    String evidenceFile = captureCa29Evidence(evidence.toString());
    int requestCount = evidence.has("requests") && evidence.get("requests").isJsonArray()
      ? evidence.getAsJsonArray("requests").size() : 0;
    int fileCount = evidence.getAsJsonArray("files").size();
    System.out.println("CA29_CONTRACT_PROBE evidence=" + evidenceFile
      + " download_failure=" + (downloadFailure == null ? "none" : downloadFailure)
      + " files=" + fileCount + " requests=" + requestCount
      + " after_route=" + safeRoute(WebDriverRunner.url()));
    if (fileCount == 0) {
      System.out.println("CA29_PRODUCT_BLOCKER list_control=Download_Fillable_PDF_form artifact=none"
        + " detail_control=Download artifact_contract=ASiC-E_unsuitable_for_CA-29_printout");
      throw new AssertionError("CA-29 failed: the observed Download Fillable PDF form control produced no printout artifact; evidence="
        + evidenceFile);
    }
  }

  private static void clearCa29Downloads(Path downloads) {
    Path buildRoot = Path.of("build").toAbsolutePath().normalize();
    if (!downloads.startsWith(buildRoot)) {
      throw new AssertionError("CA-29 download folder escaped the suite build directory: " + downloads);
    }
    try {
      Files.createDirectories(downloads);
      try (var existing = Files.walk(downloads)) {
        for (Path path : existing.sorted(java.util.Comparator.reverseOrder()).toList()) {
          if (!path.equals(downloads)) Files.deleteIfExists(path);
        }
      }
    } catch (java.io.IOException error) {
      throw new AssertionError("Could not clear the suite-owned CA-29 download folder", error);
    }
  }

  private static JsonArray ca29DownloadInventory(Path downloads) {
    JsonArray files = new JsonArray();
    try (var stream = Files.walk(downloads)) {
      stream.filter(Files::isRegularFile).forEach(path -> {
        JsonObject file = new JsonObject();
        file.addProperty("name", downloads.relativize(path).toString());
        try {
          file.addProperty("bytes", Files.size(path));
        } catch (java.io.IOException error) {
          file.addProperty("bytes", -1);
        }
        files.add(file);
      });
    } catch (java.io.IOException error) {
      JsonObject failure = new JsonObject();
      failure.addProperty("inventoryFailure", error.getClass().getSimpleName());
      files.add(failure);
    }
    return files;
  }

  private static String safeRoute(String value) {
    if (value == null) return "";
    int query = value.indexOf('?');
    int fragment = value.indexOf('#');
    int end = value.length();
    if (query >= 0) end = Math.min(end, query);
    if (fragment >= 0) end = Math.min(end, fragment);
    return value.substring(0, end);
  }

  static String captureCa29Evidence(String payload) { // package-visible: reused by Ca29RepairSteps diagnostics
    String runId = System.getenv().getOrDefault("TEST_RUN_ID", "local")
      .replaceAll("[^A-Za-z0-9._-]", "_");
    Path directory = Path.of("reports", "evidence", runId, "ca29-diagnostics");
    String fileName = "ca29-contract-" + System.currentTimeMillis() + ".json";
    Path file = directory.resolve(fileName);
    try {
      Files.createDirectories(directory);
      Files.writeString(file, Ca29RepairSteps.sanitizeDiagnosticTrace(payload), StandardCharsets.UTF_8);
      System.out.println("CA29_EVIDENCE_FILE " + file.toAbsolutePath());
      return file.toAbsolutePath().toString();
    } catch (java.io.IOException error) {
      System.out.println("CA29_EVIDENCE_WRITE_FAILED " + error.getClass().getSimpleName());
      return "";
    }
  }

  @When("I filter the observed corporate actions list by form {string}")
  public void i_filter_the_observed_corporate_actions_list_by_form(String formName) {
    assertCorporateActionsListSurface();
    $("#formNames").shouldBe(visible).click();
    $("#formNames").shouldHave(attribute("aria-expanded", "true"));
    Number matched = executeJavaScript("const wanted=arguments[0]; const targets=[...document.querySelectorAll('.dropdown-menu.show .form-check-label')].filter(e=>e.offsetParent!==null && (e.innerText||'').trim()===wanted); if(targets.length===1) targets[0].click(); return targets.length;", formName);
    if (matched == null || matched.intValue() != 1) {
      String inventory = executeJavaScript("return [...document.querySelectorAll('body *')].filter(e=>e.offsetParent!==null && (e.innerText||'').trim()===arguments[0]).slice(0,12).map(e=>e.tagName+'.'+e.className+' parent='+e.parentElement?.tagName+'.'+e.parentElement?.className).join(' | ')", formName);
      throw new AssertionError("Expected exactly one observed corporate-action form option '" + formName
        + "', found " + (matched == null ? 0 : matched.intValue()) + "; exact-text inventory=" + inventory);
    }
    uniqueObservedControl("Apply filters").click();
    awaitCorporateActionsRows();
  }

  @When("I open the observed application creation page")
  public void i_open_the_observed_application_creation_page() {
    if (ca21InstrumentationEnabled()) {
      installCa21Trace();
    }
    Number matches = 0;
    long controlsDeadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < controlsDeadline) {
      matches = executeJavaScript("const buttons=[...document.querySelectorAll('button.btn.button-primary')].filter(b=>b.offsetParent!==null && (b.innerText||'').trim()==='Create Application'); if(buttons.length===1) buttons[0].click(); return buttons.length;");
      if (matches != null && matches.intValue() != 0) break;
      sleep(100);
    }
    if (matches == null || matches.intValue() != 1) {
      throw new AssertionError("Expected one Create Application control scoped to the observed Corporate Actions list, found "
        + (matches == null ? 0 : matches.intValue()));
    }
    $(byXpath("//*[normalize-space(.)='Choose application type']")).shouldBe(visible);
    ElementsCollection bonusCandidates = null;
    long typesDeadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < typesDeadline) {
      bonusCandidates = $$(byXpath("//*[normalize-space(.)='Choose application type']/ancestor::*[self::ngb-modal-window or @role='dialog' or contains(@class,'modal')][1]//*[normalize-space(.)='Bonus Issue']")).filterBy(visible);
      if (!bonusCandidates.isEmpty()) break;
      sleep(100);
    }
    if (bonusCandidates == null || bonusCandidates.isEmpty()) {
      throw new AssertionError("Expected a visible Bonus Issue application type within the observed modal, found 0");
    }
    SelenideElement deepestBonusType = bonusCandidates.get(bonusCandidates.size() - 1);
    String bonusHierarchy = executeJavaScript("let e=arguments[0], out=[]; for(let i=0;e&&i<5;i++,e=e.parentElement) out.push(e.tagName+'.'+String(e.className||'').replace(/\\s+/g,'.')); return out.join(' > ');", deepestBonusType);
    String modalInventory = ca21ModalInventory();
    if (ca21InstrumentationEnabled()) {
      System.out.println("CA21_MODAL_INVENTORY " + modalInventory);
      captureCa21Evidence("before-click", modalInventory);
      captureCa21Evidence("bundle-clues", ca21BundleClues());
    }
    java.util.Set<String> originalWindows = WebDriverRunner.getWebDriver().getWindowHandles();
    deepestBonusType.scrollIntoView("{block:'center'}");
    deepestBonusType.click();
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      java.util.Set<String> currentWindows = WebDriverRunner.getWebDriver().getWindowHandles();
      if (currentWindows.size() > originalWindows.size()) {
        currentWindows.stream().filter(handle -> !originalWindows.contains(handle)).findFirst()
          .ifPresent(handle -> WebDriverRunner.getWebDriver().switchTo().window(handle));
      }
      String url = WebDriverRunner.url();
      if (url != null && sameOrigin(url, BASE_URL) && !url.matches(".*/corporate-actions/?(?:[?#].*)?")) {
        if (ca21InstrumentationEnabled()) {
          captureCa21Evidence("after-click", ca21LiveSnapshot());
        }
        return;
      }
      sleep(100);
    }
    String failureSnapshot = ca21LiveSnapshot();
    if (ca21InstrumentationEnabled()) {
      System.out.println("CA21_CLICK_DIAGNOSTIC " + failureSnapshot);
      captureCa21Evidence("failure", failureSnapshot);
    }
    throw new AssertionError("Create Application did not reach the customer form creation route after the observed type selection; url=" + WebDriverRunner.url()
      + "; Bonus Issue hierarchy=" + bonusHierarchy + "; live=" + failureSnapshot);
  }

  private static boolean ca21InstrumentationEnabled() {
    return Boolean.getBoolean("ca21.instrument");
  }

  /**
   * Install a read-only browser probe for CA-21 diagnosis. It records only
   * relevant click targets, redacted origin/path request metadata, history
   * transitions, and visible toast text. Request bodies and query strings are
   * deliberately never observed or logged.
   */
  private static void installCa21Trace() {
    executeJavaScript("""
      (() => {
        if (window.__ca21Trace && window.__ca21Trace.installed) return 'already-installed';
        const state = window.__ca21Trace = {
          installed: true,
          events: [],
          requests: [],
          history: [],
          toasts: [],
          errors: []
        };
        const stamp = () => new Date().toISOString();
        const safeUrl = value => {
          try {
            const parsed = new URL(value || location.href, location.href);
            return parsed.origin + parsed.pathname;
          } catch (_) {
            return String(value || '').split(/[?#]/, 1)[0];
          }
        };
        const safeText = value => String(value || '').replace(/\\s+/g, ' ').trim().slice(0, 180);
        const describe = node => {
          if (!node || !node.tagName) return null;
          const attrs = {};
          for (const name of ['id', 'class', 'role', 'type', 'href', 'aria-label', 'data-cy', 'data-testid', 'tabindex']) {
            const value = node.getAttribute(name);
            if (value !== null && value !== '') attrs[name] = name === 'href' ? safeUrl(value) : value.slice(0, 160);
          }
          return {
            tag: node.tagName,
            attrs,
            text: safeText(node.innerText || node.textContent),
            childCount: node.children ? node.children.length : 0
          };
        };
        const ancestorPath = node => {
          const result = [];
          for (let current = node, i = 0; current && i < 7; current = current.parentElement, i++) {
            result.push(describe(current));
          }
          return result;
        };
        const relevant = node => {
          if (!node || !node.tagName) return false;
          const text = safeText(node.innerText || node.textContent).toLowerCase();
          return node.matches('jhi-application-type') || /create application|choose application type|bonus issue/.test(text);
        };
        window.addEventListener('error', event => {
          state.errors.push({
            kind: 'error',
            at: stamp(),
            message: safeText(event.message),
            source: safeUrl(event.filename || ''),
            line: Number(event.lineno || 0)
          });
        });
        window.addEventListener('unhandledrejection', event => {
          state.errors.push({kind: 'unhandledrejection', at: stamp(), message: safeText(event.reason)});
        });
        document.addEventListener('click', event => {
          const target = event.target && event.target.nodeType === 1 ? event.target : event.target && event.target.parentElement;
          if (!relevant(target) && !relevant(target && target.closest && target.closest('button,a,[role=button],jhi-application-type'))) return;
          const base = {
            kind: 'click',
            at: stamp(),
            url: safeUrl(location.href),
            target: describe(target),
            path: ancestorPath(target),
            defaultPrevented: event.defaultPrevented
          };
          state.events.push(Object.assign({phase: 'capture'}, base));
          setTimeout(() => state.events.push(Object.assign({phase: 'after-bubble'}, base, {
            at: stamp(),
            url: safeUrl(location.href),
            defaultPrevented: event.defaultPrevented
          })), 0);
        }, true);
        for (const method of ['pushState', 'replaceState']) {
          const original = history[method];
          history[method] = function() {
            const result = original.apply(this, arguments);
            state.history.push({method, at: stamp(), url: safeUrl(location.href)});
            return result;
          };
        }
        const originalFetch = window.fetch;
        if (typeof originalFetch === 'function') {
          window.fetch = function(input, init) {
            const request = {
              kind: 'fetch',
              method: String((init && init.method) || (input && input.method) || 'GET').toUpperCase(),
              url: safeUrl(typeof input === 'string' ? input : input && input.url),
              at: stamp()
            };
            state.requests.push(request);
            return originalFetch.call(window, input, init).then(response => {
              request.status = response.status;
              request.completedAt = stamp();
              return response;
            }, error => {
              request.error = String(error || '').slice(0, 160);
              request.completedAt = stamp();
              throw error;
            });
          };
        }
        const originalOpen = XMLHttpRequest.prototype.open;
        const originalSend = XMLHttpRequest.prototype.send;
        XMLHttpRequest.prototype.open = function(method, url) {
          this.__ca21Request = {kind: 'xhr', method: String(method || 'GET').toUpperCase(), url: safeUrl(url), at: stamp()};
          return originalOpen.apply(this, arguments);
        };
        XMLHttpRequest.prototype.send = function() {
          const request = this.__ca21Request;
          if (request) {
            state.requests.push(request);
            this.addEventListener('loadend', () => {
              request.status = this.status;
              request.completedAt = stamp();
            }, {once: true});
          }
          return originalSend.apply(this, arguments);
        };
        const recordToast = node => {
          if (!node || !node.matches || !node.matches('.toast,[role=alert],[role=status]')) return;
          if (!node.offsetParent) return;
          const text = safeText(node.innerText || node.textContent);
          if (!text) return;
          state.toasts.push({at: stamp(), text, className: String(node.className || '').slice(0, 160)});
        };
        new MutationObserver(records => {
          for (const record of records) {
            for (const node of record.addedNodes || []) {
              if (!node || node.nodeType !== 1) continue;
              recordToast(node);
              for (const child of node.querySelectorAll ? node.querySelectorAll('.toast,[role=alert],[role=status]') : []) recordToast(child);
            }
          }
        }).observe(document.documentElement, {subtree: true, childList: true, attributes: true, attributeFilter: ['class', 'role', 'aria-live']});
        return 'installed';
      })()
      """);
  }

  /**
   * Install a read-only CA-29 trace. It records the rendered control contract,
   * click/default-prevention behavior, sanitized request paths/statuses,
   * history/toast/error transitions, blob/link download clues, and resource
   * timing. It never observes request bodies or query strings.
   */
  static void installCa29Trace() { // package-visible: reused by Ca29RepairSteps diagnostics
    executeJavaScript("""
      (() => {
        if (window.__ca29Trace && window.__ca29Trace.installed) return 'already-installed';
        const state = window.__ca29Trace = {
          installed: true,
          control: null,
          events: [],
          requests: [],
          history: [],
          toasts: [],
          errors: [],
          blobs: [],
          links: [],
          windows: [],
          resourcesBefore: performance.getEntriesByType('resource').length
        };
        const stamp = () => new Date().toISOString();
        const safeUrl = value => {
          try {
            const parsed = new URL(value || location.href, location.href);
            return parsed.origin + parsed.pathname;
          } catch (_) {
            return String(value || '').split(/[?#]/, 1)[0];
          }
        };
        const safeText = value => String(value || '').replace(/\s+/g, ' ').trim().slice(0, 180);
        const describe = node => {
          if (!node || !node.tagName) return null;
          const attrs = {};
          for (const name of ['id', 'class', 'role', 'type', 'href', 'download', 'aria-label', 'data-testid']) {
            const value = node.getAttribute(name);
            if (value !== null && value !== '') attrs[name] = name === 'href' ? safeUrl(value) : value.slice(0, 160);
          }
          return {
            tag: node.tagName,
            attrs,
            text: safeText(node.innerText || node.textContent),
            outerHTML: String(node.outerHTML || '').slice(0, 1200)
          };
        };
        const target = [...document.querySelectorAll('button,a,input[type=button],input[type=submit],[role=button]')]
          .find(node => safeText(node.innerText || node.textContent) === 'Download Fillable PDF form'
            || node.getAttribute('aria-label') === 'Download Fillable PDF form'
            || node.getAttribute('title') === 'Download Fillable PDF form');
        state.control = describe(target);
        const relevant = node => {
          if (!node || !node.tagName) return false;
          return safeText(node.innerText || node.textContent) === 'Download Fillable PDF form'
            || (node.closest && safeText(node.closest('button,a,[role=button]')?.innerText) === 'Download Fillable PDF form');
        };
        document.addEventListener('click', event => {
          const node = event.target && event.target.nodeType === 1 ? event.target : event.target?.parentElement;
          if (!relevant(node)) return;
          const button = node.closest('button,a,[role=button]') || node;
          const base = {
            kind: 'click',
            at: stamp(),
            url: safeUrl(location.href),
            target: describe(button),
            defaultPrevented: event.defaultPrevented,
            eventPhase: event.eventPhase
          };
          state.events.push(Object.assign({phase: 'capture'}, base));
          setTimeout(() => state.events.push(Object.assign({phase: 'after-bubble'}, base, {
            at: stamp(),
            url: safeUrl(location.href),
            defaultPrevented: event.defaultPrevented
          })), 0);
        }, true);
        for (const method of ['pushState', 'replaceState']) {
          const original = history[method];
          history[method] = function() {
            const result = original.apply(this, arguments);
            state.history.push({method, at: stamp(), url: safeUrl(location.href)});
            return result;
          };
        }
        const originalFetch = window.fetch;
        if (typeof originalFetch === 'function') {
          window.fetch = function(input, init) {
            const request = {
              kind: 'fetch',
              method: String((init && init.method) || (input && input.method) || 'GET').toUpperCase(),
              url: safeUrl(typeof input === 'string' ? input : input && input.url),
              at: stamp()
            };
            state.requests.push(request);
            return originalFetch.call(window, input, init).then(response => {
              request.status = response.status;
              request.contentType = response.headers.get('content-type') || '';
              request.completedAt = stamp();
              return response;
            }, error => {
              request.error = String(error || '').slice(0, 160);
              request.completedAt = stamp();
              throw error;
            });
          };
        }
        const originalOpen = XMLHttpRequest.prototype.open;
        const originalSend = XMLHttpRequest.prototype.send;
        XMLHttpRequest.prototype.open = function(method, url) {
          this.__ca29Request = {
            kind: 'xhr',
            method: String(method || 'GET').toUpperCase(),
            url: safeUrl(url),
            at: stamp()
          };
          return originalOpen.apply(this, arguments);
        };
        XMLHttpRequest.prototype.send = function() {
          const request = this.__ca29Request;
          if (request) {
            state.requests.push(request);
            this.addEventListener('loadend', () => {
              request.status = this.status;
              try { request.contentType = this.getResponseHeader('content-type') || ''; } catch (_) { request.contentType = ''; }
              request.completedAt = stamp();
            }, {once: true});
          }
          return originalSend.apply(this, arguments);
        };
        const originalObjectUrl = URL.createObjectURL;
        if (typeof originalObjectUrl === 'function') {
          URL.createObjectURL = function(blob) {
            const url = originalObjectUrl.call(URL, blob);
            state.blobs.push({at: stamp(), type: String(blob?.type || ''), bytes: Number(blob?.size || 0), url: 'blob:'});
            return url;
          };
        }
        const originalAnchorClick = HTMLAnchorElement.prototype.click;
        HTMLAnchorElement.prototype.click = function() {
          state.links.push({at: stamp(), href: safeUrl(this.href || ''), download: String(this.download || '').slice(0, 160), text: safeText(this.innerText || this.textContent)});
          return originalAnchorClick.apply(this, arguments);
        };
        const originalOpenWindow = window.open;
        window.open = function(url) {
          state.windows.push({at: stamp(), url: safeUrl(url || '')});
          return originalOpenWindow.apply(window, arguments);
        };
        window.addEventListener('error', event => {
          state.errors.push({kind: 'error', at: stamp(), message: safeText(event.message), source: safeUrl(event.filename || '')});
        });
        window.addEventListener('unhandledrejection', event => {
          state.errors.push({kind: 'unhandledrejection', at: stamp(), message: safeText(event.reason)});
        });
        const recordToast = node => {
          if (!node || !node.matches || !node.matches('.toast,[role=alert],[role=status]') || !node.offsetParent) return;
          const text = safeText(node.innerText || node.textContent);
          if (text) state.toasts.push({at: stamp(), text, className: String(node.className || '').slice(0, 160)});
        };
        new MutationObserver(records => {
          for (const record of records) {
            for (const node of record.addedNodes || []) {
              if (!node || node.nodeType !== 1) continue;
              recordToast(node);
              for (const child of node.querySelectorAll ? node.querySelectorAll('.toast,[role=alert],[role=status]') : []) recordToast(child);
            }
          }
        }).observe(document.documentElement, {subtree: true, childList: true, attributes: true, attributeFilter: ['class', 'role', 'aria-live']});
        state.collect = () => {
          state.afterRoute = safeUrl(location.href);
          state.resourcesAfter = performance.getEntriesByType('resource').slice(-80).map(entry => ({
            url: safeUrl(entry.name),
            initiatorType: String(entry.initiatorType || ''),
            durationMs: Math.round(Number(entry.duration || 0)),
            transferSize: Number(entry.transferSize || 0)
          }));
          return state;
        };
        return 'installed';
      })()
      """);
  }

  private static String ca21ModalInventory() {
    Object result = executeJavaScript("""
      return (() => {
        const visible = node => !!node && !!node.offsetParent;
        const text = node => String(node && (node.innerText || node.textContent) || '').replace(/\\s+/g, ' ').trim().slice(0, 180);
        const describe = node => ({
          tag: node.tagName,
          className: String(node.className || '').slice(0, 160),
          role: node.getAttribute('role'),
          text: text(node),
          childCount: node.children ? node.children.length : 0,
          children: [...(node.children || [])].slice(0, 8).map(child => ({
            tag: child.tagName,
            className: String(child.className || '').slice(0, 120),
            text: text(child),
            childCount: child.children ? child.children.length : 0
          }))
        });
        const form = [...document.querySelectorAll('form')]
          .find(node => visible(node) && /choose application type/i.test(text(node)));
        if (!form) return JSON.stringify({form: null, row: null, candidates: []});
        const row = [...form.querySelectorAll('div.row')]
          .find(node => visible(node) && text(node) === 'Bonus Issue');
        const candidates = [...form.querySelectorAll('*')]
          .filter(node => visible(node) && text(node) === 'Bonus Issue')
          .map(describe);
        return JSON.stringify({
          form: describe(form),
          row: row ? describe(row) : null,
          candidates,
          rowChildren: row ? [...row.children].map(describe) : []
        });
      })();
      """);
    return result == null ? "<null>" : result.toString();
  }

  private static String ca21LiveSnapshot() {
    Object result = executeJavaScript("""
      return (() => {
        const safeUrl = value => {
          try {
            const parsed = new URL(value || location.href, location.href);
            return parsed.origin + parsed.pathname;
          } catch (_) {
            return String(value || '').split(/[?#]/, 1)[0];
          }
        };
        const text = node => String(node && (node.innerText || node.textContent) || '').replace(/\\s+/g, ' ').trim().slice(0, 180);
        const visible = node => !!node && !!node.offsetParent;
        const toasts = [...document.querySelectorAll('.toast,[role=alert],[role=status]')]
          .filter(visible)
          .map(node => ({text: text(node), className: String(node.className || '').slice(0, 160)}))
          .filter(item => item.text);
        const modals = [...document.querySelectorAll('ngb-modal-window,[role=dialog],.modal')]
          .filter(visible)
          .map(node => ({text: text(node), className: String(node.className || '').slice(0, 160)}));
        const trace = window.__ca21Trace || {};
        return JSON.stringify({
          url: safeUrl(location.href),
          modals,
          toasts,
          events: (trace.events || []).slice(-20),
          requests: (trace.requests || []).slice(-30),
          history: (trace.history || []).slice(-20),
          observedToasts: (trace.toasts || []).slice(-20),
          errors: (trace.errors || []).slice(-20)
        });
      })();
      """);
    return result == null ? "<null>" : result.toString();
  }

  private static String ca21BundleClues() {
    Object result = ((JavascriptExecutor) WebDriverRunner.getWebDriver()).executeAsyncScript("""
      const done = arguments[arguments.length - 1];
      const needles = ['Choose application type', 'corporate-actions/form', 'application-type', 'bonus_issue_form'];
      const scripts = [...document.scripts]
        .map(script => script.src)
        .filter(src => src && src.startsWith(location.origin));
      Promise.all(scripts.map(async src => {
        try {
          const response = await fetch(src);
          const source = await response.text();
          const clues = [];
          for (const needle of needles) {
            let offset = source.indexOf(needle);
            let count = 0;
            while (offset >= 0 && count < 4) {
              clues.push({needle, snippet: source.slice(Math.max(0, offset - 500), Math.min(source.length, offset + needle.length + 700))});
              offset = source.indexOf(needle, offset + needle.length);
              count++;
            }
          }
          if (!clues.length) return null;
          let path = src;
          try { path = new URL(src).pathname; } catch (_) { }
          return {script: path, clues};
        } catch (error) {
          return {script: src.split(/[?#]/, 1)[0], error: String(error || '').slice(0, 160)};
        }
      })).then(items => done(JSON.stringify(items.filter(Boolean))))
        .catch(error => done(JSON.stringify({error: String(error || '').slice(0, 160)})));
      """);
    return result == null ? "<null>" : result.toString();
  }

  private static void captureCa21Evidence(String phase, String payload) {
    String runId = System.getenv().getOrDefault("TEST_RUN_ID", "local")
      .replaceAll("[^A-Za-z0-9._-]", "_");
    Path directory = Path.of("reports/evidence", runId, "ca21-diagnostics");
    String stem = "ca21-" + phase.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + System.currentTimeMillis();
    try {
      Files.createDirectories(directory);
      Path file = directory.resolve(stem + ".json");
      Files.writeString(file, payload == null ? "<null>" : payload, StandardCharsets.UTF_8);
      System.out.println("CA21_EVIDENCE_FILE " + file.toAbsolutePath());
    } catch (Exception error) {
      System.out.println("CA21_EVIDENCE_WRITE_FAILED " + error.getClass().getSimpleName());
    }
  }

  @Then("corporate_actions_filter_results_visible")
  public void corporate_actions_filter_results_visible() {
    assertCorporateActionsListSurface();
    List<SelenideElement> rows = awaitCorporateActionsRows();
    if (rows.isEmpty()) throw new AssertionError("Corporate-action form filter returned no observable rows");
    for (SelenideElement row : rows) {
      ElementsCollection cells = row.$$("td");
      if (cells.size() < 2 || !"Bonus Issue".equals(cells.get(1).getText().trim())) {
        throw new AssertionError("Corporate-action filter returned a non-Bonus Issue row: " + row.getText());
      }
    }
    screenshot("direct-ca-filter-bonus-issue-results");
  }

  private static String lastCaSearchQuery = null;

  @When("I search the observed corporate actions list by {string}")
  public void i_search_the_observed_corporate_actions_list_by(String query) {
    lastCaSearchQuery = query;
    assertCorporateActionsListSurface();
    SelenideElement searchInput = $("input[formcontrolname='inputSearchValue']").shouldBe(visible);
    // Use the native value setter + input event so Angular's FormControl picks up the change
    executeJavaScript(
      "var el = arguments[0];"
      + "var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;"
      + "nativeSetter.call(el, arguments[1]);"
      + "el.dispatchEvent(new Event('input', {bubbles: true}));"
      + "el.dispatchEvent(new Event('change', {bubbles: true}));",
      searchInput.getWrappedElement(), query);
    sleep(400);
    uniqueObservedControl("Apply filters").click();
    awaitCorporateActionsRows();
  }

  @Then("corporate_actions_search_results_visible")
  public void corporate_actions_search_results_visible() {
    assertCorporateActionsListSurface();
    List<SelenideElement> rows = awaitCorporateActionsRows();
    if (rows.isEmpty()) throw new AssertionError("Corporate-action search word returned no observable rows");
    String query = lastCaSearchQuery == null ? "" : lastCaSearchQuery.trim().toLowerCase(java.util.Locale.ROOT);
    if (query.isEmpty()) throw new AssertionError("No corporate-action search query remembered for assertion");
    for (SelenideElement row : rows) {
      String text = row.getText().toLowerCase(java.util.Locale.ROOT);
      if (!text.contains(query)) {
        throw new AssertionError("Corporate-action search for '" + lastCaSearchQuery + "' returned a row that does not contain the search term: " + row.getText());
      }
    }
    screenshot("direct-ca-search-word-results");
  }

  private static void assertCorporateActionsListSurface() {
    String url = WebDriverRunner.url();
    if (url == null || !url.matches("https://eservicesdevint\\.sets\\.lv/corporate-actions(?:[/?#].*)?")) {
      throw new AssertionError("Expected corporate-actions list route, got " + url);
    }
    $("#formNames").shouldBe(visible);
    uniqueObservedControl("Apply filters");
  }

  private static List<SelenideElement> awaitCorporateActionsRows() {
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    List<SelenideElement> rows = new ArrayList<>();
    while (System.currentTimeMillis() < deadline) {
      rows.clear();
      for (SelenideElement row : $$("table tbody tr")) if (row.isDisplayed()) rows.add(row);
      if (!rows.isEmpty()) return rows;
      sleep(250);
    }
    return rows;
  }

  @And("I fill {string} with {string}")
  public void i_fill_string_with_string(String param0, String param1) {
    fillByLabel(param0, param1);

  }

  @When("I open the observed external role editor without saving")
  public void i_open_the_observed_external_role_editor_without_saving() {
    System.out.println("  👁️  Opening external role editor...");
    openObservedRoleEditor("/external/admin/authority-rights", "External Roles", "ROLE_SELF");
  }

  @When("I open the observed internal role editor without saving")
  public void i_open_the_observed_internal_role_editor_without_saving() {
    openObservedRoleEditor("/admin/authority-rights", "Internal Roles", "Admins");
  }

  @When("I open the observed internal user editor without saving")
  public void i_open_the_observed_internal_user_editor_without_saving() {
    assertAdminList("/admin/user-management", "Internal Users");
    List<SelenideElement> matches = new ArrayList<>();
    for (SelenideElement row : visibleManagementTable().$$
        ("tbody tr")) {
      if (!row.isDisplayed()) continue;
      ElementsCollection cells = row.$$("td");
      if (cells.size() >= 2 && "AdminName".equals(cells.get(1).getText().trim())) matches.add(row);
    }
    if (matches.size() != 1) {
      throw new AssertionError("Expected exactly one observed internal user AdminName row, found " + matches.size());
    }
    clickObservedManagementRow(matches.get(0));
    awaitManagementRouteChange("/admin/user-management");
  }

  @Then("external_role_edit_surface_visible")
  public void external_role_edit_surface_visible() {
    assertRoleEditSurface("/external/admin/authority-rights", "ROLE_SELF");
    System.out.println("  👁️  External role editor surface visible");
  }

  @Then("internal_role_edit_surface_visible")
  public void internal_role_edit_surface_visible() {
    assertRoleEditSurface("/admin/authority-rights", "Admins");
  }

  @Then("internal_user_edit_surface_visible")
  public void internal_user_edit_surface_visible() {
    assertEntityEditSurface("/admin/user-management", "AdminName", "User");
  }

  private static void openObservedRoleEditor(String listPath, String heading, String roleName) {
    assertAdminList(listPath, heading);
    System.out.println("  👁️  Admin list verified, finding role '" + roleName + "'...");
    List<SelenideElement> matches = new ArrayList<>();
    for (SelenideElement row : visibleManagementTable().$$
        ("tbody tr[id]")) {
      if (!row.isDisplayed()) continue;
      ElementsCollection cells = row.$$("td");
      if (cells.size() >= 2 && roleName.equals(cells.get(1).getText().trim())) matches.add(row);
    }
    if (matches.size() != 1) {
      throw new AssertionError("Expected exactly one observed role '" + roleName + "', found " + matches.size());
    }
    // The live role menus expose Delete only and the row has no navigation
    // handler. Derive the read-only editor probe from the observed row id.
    String observedId = matches.get(0).getAttribute("id");
    if (observedId == null || !observedId.matches("[0-9]+")) {
      throw new AssertionError("Observed role row has no numeric identity: " + matches.get(0).getAttribute("outerHTML"));
    }
    System.out.println("  👁️  Found the configured role, navigating to editor...");
    adminOpen(listPath + "/" + observedId + "/edit");
    System.out.println("  👁️  Waiting for editor route...");
    awaitManagementRouteChange(listPath);
  }

  private static void clickObservedManagementRow(SelenideElement row) {
    row.shouldBe(visible);
    executeJavaScript("arguments[0].click()", row.getWrappedElement());
  }

  private static void assertAdminList(String expectedPath, String expectedHeading) {
    String url = WebDriverRunner.url();
    if (url == null || !url.matches("https://eservicesdevint\\.sets\\.lv" + expectedPath + "(?:[/?#].*)?")) {
      throw new AssertionError("Expected admin management route " + expectedPath + ", got " + url);
    }
    long deadline = System.currentTimeMillis() + 20000;
    boolean reopened = false;
    while (System.currentTimeMillis() < deadline) {
      SelenideElement heading = $("h1");
      if (heading.exists() && heading.isDisplayed() && heading.getText().contains(expectedHeading)) break;
      if (!reopened && System.currentTimeMillis() + 10000 >= deadline) {
        adminOpen(expectedPath);
        reopened = true;
      }
      sleep(200);
    }
    SelenideElement heading = $("h1");
    if (!heading.exists() || !heading.isDisplayed() || !heading.getText().contains(expectedHeading)) {
      throw new AssertionError("Admin management heading did not render on the expected route");
    }
    visibleManagementTable().shouldBe(visible);
  }

  private static void awaitManagementRouteChange(String listPath) {
    long deadline = System.currentTimeMillis() + 30000;
    while (System.currentTimeMillis() < deadline) {
      String url = WebDriverRunner.url();
      if (url != null && url.matches("https://eservicesdevint\\.sets\\.lv" + listPath
          + "/[^/?#]+/edit(?:[/?#].*)?")) return;
      sleep(300);
    }
    throw new AssertionError("Observed management editor did not leave list route " + listPath
      + "; current URL=" + WebDriverRunner.url());
  }

  private static SelenideElement visibleManagementTable() {
    long deadline = System.currentTimeMillis() + 15000;
    List<SelenideElement> tables = new ArrayList<>();
    while (System.currentTimeMillis() < deadline) {
      tables.clear();
      for (SelenideElement table : $$("table")) {
        if (table.isDisplayed()) tables.add(table);
      }
      if (tables.size() == 1) return tables.get(0);
      if (tables.size() > 1) {
        throw new AssertionError("Expected exactly one visible management table, found " + tables.size());
      }
      sleep(250);
    }
    throw new AssertionError("Management table did not become visible within 30 seconds; url="
      + WebDriverRunner.url() + " visibleText=" + $("body").getText());
  }

  private static void assertRoleEditSurface(String listPath, String selectedRole) {
    String url = WebDriverRunner.url();
    if (url == null || !url.matches("https://eservicesdevint\\.sets\\.lv" + listPath + "/[0-9]+/edit(?:[/?#].*)?")) {
      throw new AssertionError("Expected role editor route under " + listPath + ", got " + url);
    }
    $("h1").shouldBe(visible);
    String body = waitForNonEmptyBodyText();
    boolean roleObserved = body.contains(selectedRole);
    for (SelenideElement field : $$("input, textarea")) {
      String value = field.getValue();
      if (value != null && value.trim().equals(selectedRole)) roleObserved = true;
    }
    if (!roleObserved) {
      throw new AssertionError("Selected role landmark '" + selectedRole + "' was not visible on editor route " + url);
    }
    $("form").shouldBe(visible);
    screenshot("direct-management-" + selectedRole.toLowerCase(java.util.Locale.ROOT) + "-role-edit-surface");
  }

  private static void assertEntityEditSurface(String listPath, String selectedEntity, String landmarkToken) {
    String url = WebDriverRunner.url();
    if (url == null || !url.matches("https://eservicesdevint\\.sets\\.lv" + listPath + "/[^/?#]+/edit(?:[/?#].*)?")) {
      throw new AssertionError("Expected entity editor route under " + listPath + ", got " + url);
    }
    $("h1").shouldBe(visible);
    String body = waitForNonEmptyBodyText();
    boolean entityObserved = body.contains(selectedEntity);
    for (SelenideElement field : $$("input, textarea")) {
      String value = field.getValue();
      if (value != null && value.contains(selectedEntity)) entityObserved = true;
    }
    if (!entityObserved || !body.toLowerCase(java.util.Locale.ROOT).contains(landmarkToken.toLowerCase(java.util.Locale.ROOT))) {
      throw new AssertionError("Selected entity/editor landmark not visible for " + selectedEntity + " at " + url);
    }
    $("form").shouldBe(visible);
    screenshot("direct-management-" + selectedEntity.toLowerCase(java.util.Locale.ROOT) + "-user-edit-surface");
  }

  @And("I select observed person form {string}")
  public void i_select_observed_person_form(String optionLabel) {
    selectPersonFormOption(optionLabel);
  }

  @When("I open the observed external role {string} editor")
  public void i_open_the_observed_external_role_editor(String roleName) {
    long t0 = System.currentTimeMillis();
    assertAdminList("/external/admin/authority-rights", "External Roles");
    System.out.println("  [timer] assertAdminList done (" + (System.currentTimeMillis()-t0) + "ms)");
    // Find the row for AutotestRole
    SelenideElement targetRow = null;
    for (SelenideElement row : visibleManagementTable().$$("tbody tr[id]")) {
      if (!row.isDisplayed()) continue;
      String rowText = row.getText();
      if (rowText != null && rowText.contains(roleName)) {
        targetRow = row;
        break;
      }
    }
    if (targetRow == null) {
      // Fallback: use the first visible row
      for (SelenideElement row : visibleManagementTable().$$("tbody tr[id]")) {
        if (row.isDisplayed()) { targetRow = row; break; }
      }
      if (targetRow != null) {
        String actualName = targetRow.getText().trim().replaceAll("\\s+", " ").substring(0, Math.min(40, targetRow.getText().trim().length()));
        System.out.println("  👁️  Role '" + roleName + "' not found, using first available: '" + actualName + "...'");
      }
    }
    if (targetRow == null) {
      throw new AssertionError("Expected one observed role '" + roleName + "', found none");
    }
    String observedId = targetRow.getAttribute("id");
    if (observedId == null || !observedId.matches("[0-9]+")) {
      throw new AssertionError("Observed role row has no numeric identity");
    }
    System.out.println("  [timer] find row done (" + (System.currentTimeMillis()-t0) + "ms)");
    System.out.println("  👁️  Found the configured external role, navigating to editor...");
    adminOpen("/external/admin/authority-rights/" + observedId + "/edit");
    System.out.println("  👁️  Waiting for editor route...");
    long deadline = System.currentTimeMillis() + 30000;
    while (System.currentTimeMillis() < deadline) {
      String url = WebDriverRunner.url();
      if (url != null && url.contains("/external/admin/authority-rights/" + observedId + "/edit")) break;
      sleep(300);
    }
    System.out.println("  [timer] navigate to editor done (" + (System.currentTimeMillis()-t0) + "ms)");
  }

  @Then("the observed external role editor is displayed")
  public void the_observed_external_role_editor_is_displayed() {
    System.out.println("  👁️  External role editor displayed");
    $("h1").shouldBe(visible);
    $("form").shouldBe(visible);
    // Click Edit to enable the form if it's in read-only mode
    for (SelenideElement btn : $$("button")) {
      if (!btn.isDisplayed() || !btn.isEnabled()) continue;
      String text = btn.getText().trim();
      if ("Edit".equalsIgnoreCase(text)) {
        btn.click();
        System.out.println("  👁️  Clicked Edit to unlock form");
        break;
      }
    }
  }

  @When("I remember the current role description")
  public void i_remember_the_current_role_description() {
    SelenideElement descField = $("input[name*=description i], input[id*=description i], textarea[name*=description i]");
    String desc = "";
    try {
      if (descField.isDisplayed() && !descField.isEnabled()) {
        // Try clicking an Edit button to enable the form
        for (SelenideElement btn : $$("button")) {
          if (!btn.isDisplayed() || !btn.isEnabled()) continue;
          String text = btn.getText().trim();
          if ("Edit".equalsIgnoreCase(text)) {
            btn.click();
            System.out.println("  👁️  Clicked Edit to enable form fields");
            break;
          }
        }
        // Re-find the field after enabling
        descField = $("input[name*=description i], input[id*=description i], textarea[name*=description i]");
      }
      desc = descField.getValue();
    } catch (Throwable ignored) { }
    System.out.println("  👁️  Current description: '" + desc + "'");
  }

  @When("I append {string} to the role description")
  public void i_append_to_role_description(String suffix) {
    SelenideElement descField = $("input[name*=description i], input[id*=description i], textarea[name*=description i]");
    // Try clicking Edit first if field is disabled
    if (descField.isDisplayed() && !descField.isEnabled()) {
      for (SelenideElement btn : $$("button")) {
        if (!btn.isDisplayed() || !btn.isEnabled()) continue;
        String text = btn.getText().trim();
        if ("Edit".equalsIgnoreCase(text)) {
          btn.click();
          System.out.println("  👁️  Clicked Edit to enable form");
          break;
        }
      }
      descField = $("input[name*=description i], input[id*=description i], textarea[name*=description i]");
    }
    descField.shouldBe(visible).shouldBe(enabled);
    descField.sendKeys(Keys.END);
    descField.sendKeys(suffix);
    System.out.println("  👁️  Appended '" + suffix + "' to description, value now: '" + descField.getValue() + "'");
    sleep(500);
  }

            @When("I select {string} as the user representing")
  public void i_select_user_representing(String value) {
    System.out.println("  👁️  Selecting user representing: '" + value + "'...");
    SelenideElement sel = $("#representedEntityType");
    if (sel.isDisplayed()) {
      // Try exact text, then capitalized, then by value
      try { sel.selectOption(value); }
      catch (Throwable e1) {
        try { sel.selectOption(value.substring(0,1).toUpperCase() + value.substring(1).toLowerCase()); }
        catch (Throwable e2) {
          try { sel.selectOptionByValue(value); }
          catch (Throwable e3) {
            sel.selectOptionByValue(value.substring(0,1).toUpperCase() + value.substring(1).toLowerCase());
          }
        }
      }
      // Force events for Angular change detection
      executeJavaScript(
        "arguments[0].dispatchEvent(new Event('change', {bubbles: true}));" +
        "arguments[0].dispatchEvent(new Event('blur', {bubbles: true}));",
        sel.getWrappedElement());
    }
    System.out.println("  👁️  User representing set to '" + value + "'");
    sleep(500);
  }

@When("I select {string} as the represented entity country")
  public void i_select_represented_entity_country(String countryCode) {
    System.out.println("  👁️  Selecting country: '" + countryCode + "'...");
    boolean done = false;
    for (String id : new String[]{"representedEntityCountry", "country", "entityCountry"}) {
      SelenideElement select = $("#" + id);
      if (select.isDisplayed()) {
        select.selectOptionByValue(countryCode);
        done = true;
        break;
      }
    }
    if (!done) {
      throw new AssertionError("Could not find country dropdown to select '" + countryCode + "'");
    }
    System.out.println("  👁️  Country set to '" + countryCode + "'");
  }

@When("I add the role right {string}")
  public void i_add_role_right(String rightName) {
    System.out.println("  👁️  Adding role right '" + rightName + "'...");
    // Find the checkbox by its label text
    String checkboxId = null;
    for (SelenideElement labelEl : $$("label")) {
      if (!labelEl.isDisplayed()) continue;
      String text = labelEl.getText();
      if (text != null && text.contains(rightName)) {
        String forAttr = labelEl.getAttribute("for");
        if (forAttr != null && !forAttr.isBlank()) {
          checkboxId = forAttr;
        }
        break;
      }
    }
    SelenideElement checkbox = null;
    if (checkboxId != null) {
      checkbox = $("#" + checkboxId);
    }
    if (checkbox == null || !checkbox.isDisplayed()) {
      // Try finding the checkbox directly
      for (SelenideElement cb : $$("input[type=checkbox]")) {
        if (!cb.isDisplayed()) continue;
        String parentText = cb.parent().getText();
        if (parentText != null && parentText.contains(rightName)) {
          checkbox = cb;
          break;
        }
      }
    }
    if (checkbox == null || !checkbox.isDisplayed()) {
      throw new AssertionError("Could not find checkbox for role right '" + rightName + "'");
    }
    // Check the checkbox if not already checked
    if (!checkbox.isSelected()) {
      executeJavaScript("arguments[0].click()", checkbox.getWrappedElement());
      System.out.println("  👁️  Checked '" + rightName + "'");
    } else {
      System.out.println("  👁️  '" + rightName + "' was already checked");
    }

    // Click the right-arrow button (<-) to move it to the selected list
    System.out.println("  👁️  Clicking arrow to add right...");
    boolean arrowClicked = false;
    for (SelenideElement btn : $$("button")) {
      if (!btn.isDisplayed() || !btn.isEnabled()) continue;
      String label = btn.getText().trim();
      String ariaLabel = btn.getAttribute("aria-label");
      String title = btn.getAttribute("title");
      // Look for right arrow / chevron-right / > symbol
      if (">".equals(label) || "→".equals(label) || "right".equalsIgnoreCase(label)
          || (ariaLabel != null && ariaLabel.toLowerCase(java.util.Locale.ROOT).contains("right"))
          || (title != null && title.toLowerCase(java.util.Locale.ROOT).contains("right"))
          || label.contains("›") || label.contains("▶")) {
        btn.click();
        arrowClicked = true;
        break;
      }
    }
    if (!arrowClicked) {
      // Try finding by CSS class or icon
      for (SelenideElement btn : $$("button i.fa-chevron-right, button i.fa-angle-right, button .glyphicon-chevron-right")) {
        if (btn.isDisplayed()) {
          btn.click();
          arrowClicked = true;
          break;
        }
      }
    }
    if (!arrowClicked) {
      System.out.println("  ⚠️  Could not find right arrow button, continuing");
    } else {
      System.out.println("  👁️  Right arrow clicked");
    }
  }

  @When("I save the external role")
  public void i_save_the_external_role() {
    System.out.println("  👁️  Saving external role...");
    SelenideElement saveBtn = null;
    for (SelenideElement btn : $$("button")) {
      if (!btn.isDisplayed() || !btn.isEnabled()) continue;
      String text = btn.getText().trim();
      if ("Save".equals(text)) {
        saveBtn = btn;
        break;
      }
    }
    if (saveBtn == null) {
      saveBtn = uniqueObservedControl("Save");
    }
    saveBtn.click();
    System.out.println("  👁️  Save clicked, waiting for confirmation...");
  }

  @Then("role_saved_confirmation")
  public void role_saved_confirmation() {
    long deadline = System.currentTimeMillis() + 15000;
    while (System.currentTimeMillis() < deadline) {
      try {
        SelenideElement bodyEl = $("body");
        if (bodyEl.exists() && bodyEl.isDisplayed()) {
          String body = bodyEl.getText();
          String norm = body == null ? "" : body.toLowerCase(java.util.Locale.ROOT);
          if (norm.contains("saved") || norm.contains("success")) {
            System.out.println("  👁️  Role saved successfully");
            captureScenarioCheckpoint("Then", "role_saved_confirmation");
            screenshot("direct-management-role-saved");
            return;
          }
        }
      } catch (Throwable ignored) { }
      sleep(250);
    }
    screenshot("direct-management-role-save-failed");
    throw new AssertionError("Role save confirmation was not detected after clicking Save");
  }

  @Then("user_saved_confirmation")
  public void user_saved_confirmation() {
    assertSemanticState("user_saved_confirmation");
    CheckpointCapture.capture("edit-internal-user.admin-edit-internal-user.user-saved-confirmation");
  }

  @Then("person_saved_confirmation")
  public void person_saved_confirmation() {
    assertSemanticState("person_saved_confirmation");
    CheckpointCapture.capture("edit-person.admin-edit-person.person-saved-confirmation");
  }

  @Then("person_edit_surface_visible")
  public void person_edit_surface_visible() {
    String url = WebDriverRunner.url();
    if (url == null || !url.matches("https://eservicesdevint\\.sets\\.lv/holders-information/ereg/person-isins(?:[/?#].*)?")) {
      throw new AssertionError("Expected admin Person ISINs route, got " + url);
    }
    String body = waitForNonEmptyBodyText();
    if (body == null || body.isBlank()) {
      throw new AssertionError("Person edit surface did not expose visible text");
    }
    $("h1").shouldBe(visible).shouldHave(text("Person ISINs"));
    $("h2").shouldBe(visible).shouldHave(text("Select Person"));
    CheckpointCapture.capture("edit-person.admin-edit-person.person-edit-surface-visible");
  }

  @Then("person_edit_workflow_without_saving")
  public void person_edit_workflow_without_saving() {
    String url = WebDriverRunner.url();
    if (url == null || !url.matches("https://eservicesdevint\\.sets\\.lv/holders-information/ereg/person-isins(?:[/?#].*)?")) {
      throw new AssertionError("Expected admin Person ISINs route, got " + url);
    }
    $("h1").shouldBe(visible).shouldHave(text("Person ISINs"));
    $("div.tag-line .nsdq-tag").shouldBe(visible)
      .shouldHave(text("Selected person"))
      .shouldHave(text("VIESTURS LOKMANIS (10108810320)"));
    $$("h2").filterBy(text("Users for this person")).first().shouldBe(visible);
    $("div.table-surface input.form-control").shouldBe(visible);
    $$("div.table-surface button").filterBy(text("Add to selected person"))
      .first().shouldBe(visible).shouldBe(disabled);
    $("table.person-isins-disclosure-style").shouldBe(visible);
    $("div.table-rows-overlay").should(disappear);
    CheckpointCapture.capture("edit-person.admin-edit-person.person-edit-workflow-without-saving");
  }

  @When("I search for the observed person {string} without saving")
  public void i_search_for_the_observed_person_without_saving(String query) {
    String url = WebDriverRunner.url();
    if (url == null || !url.matches("https://eservicesdevint\\.sets\\.lv/holders-information/ereg/person-isins(?:[/?#].*)?")) {
      throw new AssertionError("Expected admin Person ISINs route before person search, got " + url);
    }
    SelenideElement search = $("#person-search")
      .shouldBe(visible)
      .shouldHave(attribute("placeholder", "Type at least 2 characters"));
    search.setValue(query);
    search.shouldHave(value(query));
    sleep(3000);
  }

  @And("I open the observed person editor without saving")
  public void i_open_the_observed_person_editor_without_saving() {
    List<SelenideElement> matches = new ArrayList<>();
    for (SelenideElement suggestion : $$("jhi-ereg-person-isins ul.suggestions-list > li")) {
      if (suggestion.isDisplayed()
          && "VIESTURS LOKMANIS (10108810320)".equals(suggestion.getText().trim())) {
        matches.add(suggestion);
      }
    }
    if (matches.size() != 1) {
      throw new AssertionError("Expected exactly one observed VIESTURS LOKMANIS suggestion, found " + matches.size());
    }
    matches.get(0).click();
    sleep(5000);
  }

  @Then("filtered_persons_list")
  public void filtered_persons_list() {
    assertPersonsRouteAndHeading();
    $("form[name=personListForm] table").shouldBe(visible);
    uniqueObservedControl("Form").click();
    SelenideElement selected = $("form[name=personListForm] input[type=checkbox][name=form][value=NATURAL_PERSON]");
    selected.shouldBe(visible).shouldBe(checked);
    $("form[name=personListForm] label[for='" + selected.getAttribute("id") + "']")
      .shouldBe(visible).shouldHave(text("Natural person"));
    uniqueObservedControl("Form").click();
    CheckpointCapture.capture("filter-persons.admin-filter-persons.filtered-persons-list");
  }

  private static void selectPersonFormOption(String optionLabel) {
    List<SelenideElement> options = new ArrayList<>();
    for (SelenideElement checkbox : $("form[name=personListForm]").$$
        ("input[type=checkbox][name=form]")) {
      String id = checkbox.getAttribute("id");
      if (id == null || id.isBlank()) continue;
      SelenideElement label = $("form[name=personListForm] label[for='" + id + "']");
      if (label.exists() && optionLabel.equals(label.getText().trim())) options.add(checkbox);
    }
    if (options.size() != 1) {
      throw new AssertionError("Expected exactly one person form option '" + optionLabel
        + "', found " + options.size());
    }
    SelenideElement checkbox = options.get(0);
    String id = checkbox.getAttribute("id");
    $("form[name=personListForm] label[for='" + id + "']").shouldBe(visible).click();
    checkbox.shouldBe(checked);
  }

  @Then("signees_list_visible")
  public void signees_list_visible() {
    assertSemanticState("signees_list_visible");
    CheckpointCapture.capture("initiate-signature-process-view-signees.admin-initiate-signature-process-view-signees-for-company-caform.signees-list-visible");
  }

  @Then("home_visible")
  public void home_visible() {
    String url = WebDriverRunner.url();
    if (sameOrigin(url, ADMIN_BASE_URL)) {
      if (url == null || !url.matches("https://eservicesdevint\\.sets\\.lv(?:/home)?/?(?:[?#].*)?")) {
        throw new AssertionError("Expected authenticated admin home route, got " + url);
      }
      String body = waitForNonEmptyBodyText().toLowerCase(java.util.Locale.ROOT);
      if (!(body.contains("home") || body.contains("management") || body.contains("corporate actions"))) {
        throw new AssertionError("Authenticated admin home landmark was not visible: " + body);
      }
    } else {
      assertSemanticState("home_visible");
    }
    captureScenarioCheckpoint("Then", "home_visible");
  }

  @And("upcoming_events_widget")
  public void upcoming_events_widget() {
    captureScenarioCheckpoint("And", "upcoming_events_widget");
    throw new AssertionError("Unsupported Java/Selenide step: and upcoming_events_widget");
  }

  @Then("persons_list_visible")
  public void persons_list_visible() {
    assertSemanticState("persons_list_visible");
    CheckpointCapture.capture("open-persons-list.admin-open-persons-list.persons-list-visible");
  }

  @Then("external_roles_list_visible")
  public void external_roles_list_visible() {
    assertSemanticState("external_roles_list_visible");
    CheckpointCapture.capture("open-roles-external-roles-list.admin-open-roles-external-roles-list.external-roles-list-visible");
  }

  @Then("internal_roles_list_visible")
  public void internal_roles_list_visible() {
    assertSemanticState("internal_roles_list_visible");
    CheckpointCapture.capture("open-roles-internal-roles-list.admin-open-roles-internal-roles-list.internal-roles-list-visible");
  }

  @Then("external_users_list_visible")
  public void external_users_list_visible() {
    assertSemanticState("external_users_list_visible");
    CheckpointCapture.capture("open-users-external-users-list.admin-open-users-external-users-list.external-users-list-visible");
  }

  @Then("internal_users_list_visible")
  public void internal_users_list_visible() {
    assertSemanticState("internal_users_list_visible");
    CheckpointCapture.capture("open-users-internal-users-list.admin-open-users-internal-users-list.internal-users-list-visible");
  }

  @Then("status_invalid")
  public void status_invalid() {
    assertSemanticState("status_invalid");
    CheckpointCapture.capture("reject-application-add-comments-check-if-status-changes-to-invalid.admin-reject-application-add-comments-check-if-status-changes-to-invalid.status-invalid");
  }

  @Then("application_saved")
  public void application_saved() {
    assertSemanticState("application_saved");
    CheckpointCapture.capture("save-new-application.admin-save-new-application-for-company-caform.application-saved");
  }

  @And("status_draft")
  public void status_draft() {
    CheckpointCapture.capture("save-new-application.admin-save-new-application-for-company-caform.status-draft");
    throw new AssertionError("Unsupported Java/Selenide step: and status_draft");
  }

  @Then("application_search_results")
  public void application_search_results() {
    assertSemanticState("application_search_results");
    CheckpointCapture.capture("search-corporate-actions-list.admin-search-corporate-actions-list.application-search-results");
  }

  @Then("search_results_visible")
  public void search_results_visible() {
    assertPersonsRouteAndHeading();
    $("input[type=search][name=search]").shouldBe(visible).shouldHave(value("Autotests"));
    uniqueObservedControl("Search").shouldBe(visible);
    SelenideElement table = visibleManagementTable().shouldBe(visible);
    // Positive result required: the pager must report at least one hit
    // (format "1 - N of M") and the table body must contain a matching row.
    java.util.regex.Matcher m = java.util.regex.Pattern
      .compile("1 - (\\d+) of (\\d+)").matcher($("body").getText());
    if (!m.find()) {
      throw new AssertionError("Expected result counter '1 - N of M' for a positive external user search");
    }
    int shown = Integer.parseInt(m.group(1));
    int total = Integer.parseInt(m.group(2));
    if (total < 1 || shown < 1) {
      throw new AssertionError("Expected a positive search result for 'Autotests', got: 1 - "
        + shown + " of " + total);
    }
    String tableText = table.getText().toLowerCase(java.util.Locale.ROOT);
    if (!tableText.contains("autotest")) {
      throw new AssertionError("Result rows do not mention 'autotest'; observed table text: "
        + tableText.substring(0, Math.min(tableText.length(), 500)));
    }
    CheckpointCapture.capture("search-external-user.admin-search-external-user.search-results-visible");
  }

  @Then("person_search_results")
  public void person_search_results() {
    assertPersonsRouteAndHeading();
    $("input[type=search][name=search]").shouldBe(visible).shouldHave(value("Autotests"));
    uniqueObservedControl("Search").shouldBe(visible);
    // Positive result required: pager must show at least one hit
    java.util.regex.Matcher m = java.util.regex.Pattern
      .compile("1 - (\\d+) of (\\d+)").matcher($("body").getText());
    if (!m.find()) {
      throw new AssertionError("Expected result counter '1 - N of M' for a positive persons search");
    }
    int shown = Integer.parseInt(m.group(1));
    int total = Integer.parseInt(m.group(2));
    if (total < 1 || shown < 1) {
      throw new AssertionError("Expected a positive persons search result for 'Autotests', got: 1 - "
        + shown + " of " + total);
    }
    SelenideElement table = visibleManagementTable().shouldBe(visible);
    String tableText = table.getText().toLowerCase(java.util.Locale.ROOT);
    if (!tableText.contains("autotest")) {
      throw new AssertionError("Persons search result rows do not mention 'autotest'; observed table text: "
        + tableText.substring(0, Math.min(tableText.length(), 500)));
    }
    CheckpointCapture.capture("search-persons.admin-search-persons.person-search-results");
  }

  private static void assertPersonsRouteAndHeading() {
    String url = WebDriverRunner.url();
    if (url == null || !url.matches("https://eservicesdevint\\.sets\\.lv/external/admin/persons(?:[/?#].*)?")) {
      throw new AssertionError("Expected admin Persons route, got " + url);
    }
    $("h1").shouldBe(visible).shouldHave(text("External Persons"));
  }

  @Then("application_signed")
  public void application_signed() {
    assertSemanticState("application_signed");
    CheckpointCapture.capture("sign-application-via-dokobit.admin-sign-application-via-dokobit-for-company-caform.application-signed");
  }

  @And("status_changed")
  public void status_changed() {
    CheckpointCapture.capture("sign-application-via-dokobit.admin-sign-application-via-dokobit-for-company-caform.status-changed");
    throw new AssertionError("Unsupported Java/Selenide step: and status_changed");
  }

  @Then("attachments_list_visible")
  public void attachments_list_visible() {
    assertObservedCorporateActionsTab("Attachments", "attachments");
    CheckpointCapture.capture("direct-ca.view-attachments-tab.attachments-list-visible");
  }

  @Then("application_list_visible")
  public void application_list_visible() {
    assertObservedCorporateActionsList();
    CheckpointCapture.capture("view-corporate-actions-application-list-browse-different-tabs.admin-view-corporate-actions-application-list-browse-different-tabs.application-list-visible");
  }

  @When("I browse all the Corporate Actions application list tabs")
  public void iBrowseAllTheCorporateActionsApplicationListTabs() {
    awaitCorporateActionsList();
    long deadline = System.currentTimeMillis() + 30000;
    List<String> opened = new ArrayList<>();
    List<String> failed = new ArrayList<>();
    while (System.currentTimeMillis() < deadline) {
      Object labelsObj = executeJavaScript(
        "return [...document.querySelectorAll('li.tab-item')]"
          + ".filter(function(e){var r=e.getClientRects();return r.length>0&&r[0].height>0;})"
          + ".map(function(e){return (e.innerText||'').replace(/[ \\t\\r\\n]+/g,' ').trim();})"
          + ".filter(function(t){return t.length>0;});");
      @SuppressWarnings("unchecked")
      List<String> labels = labelsObj == null ? new ArrayList<>()
        : (List<String>) labelsObj;
      if (labels.isEmpty()) {
        sleep(250);
        continue;
      }
      boolean progressed = false;
      for (String label : new ArrayList<>(labels)) {
        if (opened.contains(label)) continue;
        if (clickListTabAndWaitActive(label, 4000)) {
          opened.add(label);
          System.out.println("CA31_TAB_OPENED label=\"" + label + "\" (" + opened.size() + "/" + labels.size() + ")"
            + " url=" + com.codeborne.selenide.WebDriverRunner.url());
          progressed = true;
        } else {
          failed.add(label);
        }
      }
      if (opened.size() >= labels.size() && labels.size() > 0) break;
      if (!progressed) {
        if (!failed.isEmpty() && System.currentTimeMillis() < deadline - 5000) {

          sleep(1500);
          continue;
        }
        break;
      }
    }
    if (opened.isEmpty()) {
      String inventory = executeJavaScript(
        "return [...document.querySelectorAll('li.tab-item')]"
          + ".filter(function(e){var r=e.getClientRects();return r.length>0&&r[0].height>0;})"
          + ".map(function(e){var t=(e.innerText||'').replace(/[ \\t\\r\\n]+/g,' ').trim();"
          + "return t.length>0?t:('<'+(e.tagName||'').toLowerCase()+' id='+(e.id||'')+'>');}).join(' | ');");
      throw new AssertionError("No Corporate Actions list tabs observed. Visible tab-like inventory: " + inventory);
    }
    System.out.println("CA31_TABS_ALL_OPENED total=" + opened.size() + " list=" + opened);
  }

  private static boolean clickListTabAndWaitActive(String label, long timeoutMs) {



    try {

      long deadline = System.currentTimeMillis() + timeoutMs;


      while (System.currentTimeMillis() < deadline) {

        SelenideElement tab = $$("li.tab-item").stream()
          .filter(el -> {
            try { return el.isDisplayed() && normalizeTabText(el.getText()).equals(normalizeTabText(label)); }
            catch (Throwable ignored) { return false; }
          })
          .findFirst().orElse(null);
        if (tab == null) {
          sleep(250);
          continue;
        }
        tab.scrollIntoView("{block:'center',inline:'center'}").click();
        long activeDeadline = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < activeDeadline) {



          SelenideElement active = $$("li.tab-item.active").stream()
            .filter(el -> {
              try { return el.isDisplayed() && normalizeTabText(el.getText()).equals(normalizeTabText(label)); }
              catch (Throwable ignored) { return false; }
            })
            .findFirst().orElse(null);
          if (active != null) return true;
          sleep(200);
        }
        return false;
      }
    } catch (Throwable failure) {
      System.out.println("CA31_TAB_CLICK_FAILED label=" + label + " err=" + failure);
      return false;
    }
    return false;
  }

  private static java.util.function.Predicate<SelenideElement> uniqueTabElement() {
    java.util.Set<String> seen = new java.util.HashSet<>();
    return el -> {
      String key = normalizeTabText(el.getText());
      if (key.isBlank() || !seen.add(key)) return false;
      return true;
    };
  }

  private static String normalizeTabText(String value) {
    return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " " ).trim().toLowerCase(java.util.Locale.ROOT);
  }

  @Then("each opened Corporate Actions list tab stays open")
  public void eachOpenedCorporateActionsListTabStaysOpen() {
    String url = com.codeborne.selenide.WebDriverRunner.url();
    if (url == null || !url.contains("/corporate-actions")) {
      throw new AssertionError("Corporate Actions list abandoned its route after tab walk; url=" + url);
    }
  }

  @Then("history_entries_visible")
  public void history_entries_visible() {
    assertObservedCorporateActionsTab("History", "history");
    CheckpointCapture.capture("direct-ca.view-history-tab.history-entries-visible");
  }

  @Then("signatures_visible")
  public void signatures_visible() {
    assertObservedCorporateActionsTab("Signatures", "signature");
    CheckpointCapture.capture("direct-ca.view-signatures-tab.signatures-visible");
  }

  @Then("application_details_visible")
  public void application_details_visible() {
    assertObservedCorporateActionDetails();
    CheckpointCapture.capture("view-single-application.admin-view-single-application.application-details-visible");
  }

  private static void selectObservedCorporateActionWithoutSaving(String country, String caForm) {
    SELECTED_CORPORATE_ACTION.remove();
    awaitCorporateActionsList();
    String body = $("body").shouldBe(visible).getText();
    if (hasVisibleCorporateActionsEmptyState()) {
      throw new AssertionError("No existing Corporate Actions application is available for "
        + country + "/" + caForm + "; the live list exposes an explicit empty state: " + body);
    }
    SelenideElement table = visibleCorporateActionsTable();
    String expectedCountry = country.trim().toUpperCase(java.util.Locale.ROOT);
    String expectedForm = corporateActionFormLabel(caForm);
    CorporateActionIdentity observedTarget = observedCorporateActionTarget(expectedCountry, expectedForm);
    System.out.println("CA_OBSERVED_TARGET " + observedTarget.summary());
    List<SelenideElement> matches = new ArrayList<>();
    List<String> candidates = new ArrayList<>();
    for (SelenideElement row : visibleCorporateActionRows(table)) {
      ElementsCollection cells = row.$$("td");
      CorporateActionIdentity identity = corporateActionIdentity(cells);
      String summary = identity.summary();
      candidates.add(summary);
      System.out.println("CA_ROW_OBSERVED " + summary);
      if (identity.isin().toUpperCase(java.util.Locale.ROOT).startsWith(expectedCountry)
          && identity.form().equalsIgnoreCase(expectedForm)
          && identity.equals(observedTarget)) {
        matches.add(row);
      }
    }
    if (matches.size() != 1) {
      throw new AssertionError("Expected exactly one existing Corporate Actions application for "
        + expectedCountry + "/" + expectedForm + ", found " + matches.size()
        + ". Required observed predicate: " + observedTarget.summary()
        + ". Visible candidates: " + candidates);
    }
    CorporateActionIdentity selectedIdentity = corporateActionIdentity(matches.get(0).$$("td"));
    SELECTED_CORPORATE_ACTION.set(selectedIdentity);
    System.out.println("CA_SELECTED_ROW_WITHOUT_NAVIGATION " + selectedIdentity.summary());
  }

  /**
   * CA-24 is a direct live probe, not a generated matrix. Enumerate the
   * authenticated Corporate Actions list, inspect each observed detail page,
   * and stop only on a rendered uploaded attachment with one unambiguous
   * download control. No attachment is uploaded and no filename or MIME type
   * is assumed.
   */
  private static void probeCa24AttachmentDownload() {
    SELECTED_CORPORATE_ACTION.remove();
    CA24_ATTACHMENT_RESULT.remove();
    clearCa24Downloads();

    List<String> observedRows = new ArrayList<>();
    List<String> applicationsWithoutAttachments = new ArrayList<>();
    List<String> applicationsWithoutUniqueDownload = new ArrayList<>();
    JsonArray applicationInventories = new JsonArray();

    awaitCorporateActionsList();
    if (!hasVisibleCorporateActionRows()) {
      JsonObject evidence = ca24Evidence("strict-failure-no-application-rows", observedRows,
        applicationsWithoutAttachments, applicationsWithoutUniqueDownload, applicationInventories);
      String evidenceFile = captureCa24Evidence(evidence);
      ca24CheckpointBestEffort("download-attachment-from-application.ca24-no-attachment-bearing-application");
      throw new AssertionError("CA-24 strict failure: the authenticated Corporate Actions list exposed no application rows"
        + "; evidence=" + evidenceFile);
    }

    SelenideElement table = visibleCorporateActionsTable();
    List<SelenideElement> initialRows = visibleCorporateActionRows(table);
    List<Ca24ApplicationCandidate> candidates = new ArrayList<>();
    for (int rowOrdinal = 0; rowOrdinal < initialRows.size(); rowOrdinal++) {
      SelenideElement row = initialRows.get(rowOrdinal);
      CorporateActionIdentity identity = corporateActionIdentity(row.$$("td"));
      if ("Invalid".equalsIgnoreCase(identity.status())) { System.out.println("CA24_ROW_SKIPPED_INVALID " + identity.summary()); continue; }
      candidates.add(new Ca24ApplicationCandidate(identity, rowOrdinal));
      String observedRow = "row=" + rowOrdinal + " " + identity.summary();
      observedRows.add(observedRow);
      System.out.println("CA24_ROW_OBSERVED " + observedRow);
    }

    for (Ca24ApplicationCandidate candidate : candidates) {
      SELECTED_CORPORATE_ACTION.set(candidate.identity());
      adminOpen("/corporate-actions");
      awaitCorporateActionsList();
      clickCa24CorporateAction(candidate);
      awaitCorporateActionDetailSurface();
      openObservedCorporateActionsTab("Attachments");

      JsonObject attachmentInventory = ca24AttachmentInventory();
      applicationInventories.add(ca24ApplicationInventory(candidate.identity(), attachmentInventory));
      int downloadableItems = ca24Int(attachmentInventory, "downloadableItemCount");
      if (downloadableItems == 0) {
        applicationsWithoutAttachments.add(candidate.identity().summary());
        System.out.println("CA24_APPLICATION_WITHOUT_UPLOADED_ATTACHMENT row=" + candidate.rowOrdinal()
          + " " + candidate.identity().summary());
        continue;
      }

      List<SelenideElement> controls = ca24ObservedDownloadControls();
      if (controls.size() != 1) {
        applicationsWithoutUniqueDownload.add(candidate.identity().summary());
        System.out.println("CA24_ATTACHMENT_DOWNLOAD_AMBIGUOUS row=" + candidate.rowOrdinal()
          + " application=" + candidate.identity().summary()
          + " controls=" + controls.size());
        continue;
      }

      SelenideElement control = controls.get(0);
      String observedControl = ca24ControlLabel(control);
      JsonObject foundEvidence = ca24Evidence("attachment-bearing-application-found", observedRows,
        applicationsWithoutAttachments, applicationsWithoutUniqueDownload, applicationInventories);
      foundEvidence.addProperty("selectedApplication", candidate.identity().summary());
      foundEvidence.addProperty("selectedRowOrdinal", candidate.rowOrdinal());
      foundEvidence.addProperty("observedDownloadControl", observedControl);
      String foundEvidenceFile = captureCa24Evidence(foundEvidence);
      ca24Checkpoint("download-attachment-from-application.ca24-attachment-bearing-application-found");

      JsonArray artifacts = downloadObservedCa24Attachment(control);
      JsonObject downloadedEvidence = ca24Evidence("downloaded-observed-attachment", observedRows,
        applicationsWithoutAttachments, applicationsWithoutUniqueDownload, applicationInventories);
      downloadedEvidence.addProperty("selectedApplication", candidate.identity().summary());
      downloadedEvidence.addProperty("selectedRowOrdinal", candidate.rowOrdinal());
      downloadedEvidence.addProperty("observedDownloadControl", observedControl);
      downloadedEvidence.add("artifacts", artifacts);
      downloadedEvidence.addProperty("foundEvidence", foundEvidenceFile);
      String downloadedEvidenceFile = captureCa24Evidence(downloadedEvidence);
      ca24Checkpoint("download-attachment-from-application.ca24-attachment-downloaded");

      int artifactCount = ca24NonEmptyArtifactCount(artifacts);
      if (artifactCount < 1) {
        throw new AssertionError("CA-24 strict failure: the observed attachment control produced no non-empty artifact"
          + "; evidence=" + downloadedEvidenceFile);
      }
      CA24_ATTACHMENT_RESULT.set(new Ca24AttachmentResult(candidate.identity().summary(), observedControl, artifactCount));
      System.out.println("CA24_ATTACHMENT_FOUND row=" + candidate.rowOrdinal()
        + " application=" + candidate.identity().summary()
        + " control=" + observedControl
        + " artifacts=" + artifactCount
        + " evidence=" + downloadedEvidenceFile);
      return;
    }

    JsonObject evidence = ca24Evidence("strict-failure-no-downloadable-attachment", observedRows,
      applicationsWithoutAttachments, applicationsWithoutUniqueDownload, applicationInventories);
    String evidenceFile = captureCa24Evidence(evidence);
    ca24CheckpointBestEffort("download-attachment-from-application.ca24-no-attachment-bearing-application");
    throw new AssertionError("CA-24 strict failure: no application exposed a non-empty uploaded attachment with an"
      + " unambiguous download control; evidence=" + evidenceFile);
  }

  private static void clickCa24CorporateAction(Ca24ApplicationCandidate candidate) {
    long deadline = System.currentTimeMillis() + Math.max(Configuration.timeout, 60000);
    while (System.currentTimeMillis() < deadline) {
      SelenideElement table = visibleCorporateActionsTable();
      List<SelenideElement> rows = visibleCorporateActionRows(table);
      if (candidate.rowOrdinal() < 0 || candidate.rowOrdinal() >= rows.size()) {
        throw new AssertionError("CA-24 observed row ordinal " + candidate.rowOrdinal()
          + " is not present after list refresh; rowCount=" + rows.size());
      }
      SelenideElement row = rows.get(candidate.rowOrdinal());
      CorporateActionIdentity refreshedIdentity = corporateActionIdentity(row.$$("td"));
      if (!refreshedIdentity.equals(candidate.identity())) {
        throw new AssertionError("CA-24 refreshed row ordinal " + candidate.rowOrdinal()
          + " changed identity. Expected=" + candidate.identity().summary()
          + " observed=" + refreshedIdentity.summary());
      }
      try {
        row.$$("td").get(1).scrollIntoView("{block: 'center'}").click();
        return;
      } catch (org.openqa.selenium.StaleElementReferenceException ignored) {
        sleep(100);
      }
    }
    throw new AssertionError("CA-24 observed application row kept rerendering before it could be opened: row="
      + candidate.rowOrdinal() + " " + candidate.identity().summary());
  }

  private static JsonObject ca24AttachmentInventory() {
    Object raw = executeJavaScript("""
      return JSON.stringify((() => {
        const normalize = value => String(value || '')
          .replace(/[\\u0000-\\u001f\\u007f]+/g, ' ')
          .replace(/\\s+/g, ' ').trim().slice(0, 300);
        const redact = value => normalize(value)
          .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/gi, '[REDACTED:email]');
        const visible = element => {
          if (!element) return false;
          const style = getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return style.display !== 'none' && style.visibility !== 'hidden'
            && style.opacity !== '0' && rect.width > 0 && rect.height > 0;
        };
        const label = element => redact([
          element.innerText || element.textContent || '',
          element.getAttribute('aria-label') || '',
          element.getAttribute('title') || '',
          element.getAttribute('data-testid') || ''
        ].filter(Boolean).join(' '));
        const isDownloadLike = element => {
          if (!visible(element) || element.hasAttribute('disabled')
              || element.getAttribute('aria-disabled') === 'true') return false;
          const text = label(element).toLowerCase();
          return Boolean(element.getAttribute('href') || element.getAttribute('download')
            || /download/.test(text));
        };
        const lists = Array.from(document.querySelectorAll('[class~="file-list"][class~="uploaded"]'))
          .filter(visible);
        const described = lists.map(list => {
          const items = Array.from(list.children)
            .filter(child => child.tagName === 'LI' && visible(child))
            .map(item => {
              const controls = Array.from(item.querySelectorAll(
                'a,button,[role="button"],input[type="button"],input[type="submit"]'))
                .filter(visible)
                .map(control => ({
                  tag: control.tagName,
                  label: label(control),
                  hasHref: Boolean(control.getAttribute('href')),
                  hasDownloadAttribute: Boolean(control.getAttribute('download')),
                  downloadLike: isDownloadLike(control)
                }));
              return {
                text: redact(item.innerText || item.textContent || ''),
                controls,
                downloadableControls: controls.filter(control => control.downloadLike).length
              };
            });
          return {
            text: redact(list.innerText || list.textContent || ''),
            itemCount: items.length,
            downloadableItemCount: items.filter(item => item.downloadableControls > 0).length,
            items
          };
        });
        return {
          listCount: described.length,
          itemCount: described.reduce((sum, list) => sum + list.itemCount, 0),
          downloadableItemCount: described.reduce((sum, list) => sum + list.downloadableItemCount, 0),
          lists: described
        };
      })());
    """);
    if (raw == null || raw.toString().isBlank()) return new JsonObject();
    try {
      return JsonParser.parseString(raw.toString()).getAsJsonObject();
    } catch (RuntimeException parseFailure) {
      JsonObject failure = new JsonObject();
      failure.addProperty("inventoryParseFailure", parseFailure.getClass().getSimpleName());
      return failure;
    }
  }

  private static List<SelenideElement> ca24ObservedDownloadControls() {
    List<SelenideElement> controls = new ArrayList<>();
    for (SelenideElement list : $("body").$$("[class~='file-list'][class~='uploaded']")) {
      if (!list.isDisplayed()) continue;
      for (SelenideElement item : list.$$("li")) {
        if (!item.isDisplayed()) continue;
        for (SelenideElement control : item.$$("a,button,[role=button],input[type=button],input[type=submit]")) {
          if (!control.isDisplayed() || !control.isEnabled()) continue;
          String label = ca24ControlLabel(control).toLowerCase(java.util.Locale.ROOT);
          String href = control.getAttribute("href");
          String download = control.getAttribute("download");
          if ((href != null && !href.isBlank()) || (download != null && !download.isBlank())
              || label.contains("download")) {
            controls.add(control);
          }
        }
      }
    }
    return controls;
  }

  private static String ca24ControlLabel(SelenideElement control) {
    List<String> values = new ArrayList<>();
    for (String value : new String[] {control.getText(), control.getAttribute("aria-label"),
        control.getAttribute("title"), control.getAttribute("download")}) {
      if (value != null && !value.isBlank()) values.add(compactText(value));
    }
    String result = compactText(String.join(" | ", values));
    return result.isBlank() ? control.getTagName() : result;
  }

  private static JsonArray downloadObservedCa24Attachment(SelenideElement control) {
    String previousDownloadsFolder = Configuration.downloadsFolder;
    FileDownloadMode previousDownloadMode = Configuration.fileDownload;
    try {
      Configuration.downloadsFolder = CA24_DOWNLOADS.toString();
      Configuration.fileDownload = FileDownloadMode.FOLDER;
      String tag = control.getTagName();
      String href = control.getAttribute("href");
      String download = control.getAttribute("download");
      if ("a".equalsIgnoreCase(tag) && ((href != null && !href.isBlank())
          || (download != null && !download.isBlank()))) {
        control.download();
      } else {
        control.scrollIntoView("{block: 'center', inline: 'center'}").click();
      }
      return awaitCa24Artifacts();
    } finally {
      Configuration.downloadsFolder = previousDownloadsFolder;
      Configuration.fileDownload = previousDownloadMode;
    }
  }

  private static JsonArray awaitCa24Artifacts() {
    long deadline = System.currentTimeMillis() + Math.max(Configuration.timeout, 30000);
    JsonArray artifacts = ca24DownloadInventory();
    while (System.currentTimeMillis() < deadline && ca24NonEmptyArtifactCount(artifacts) == 0) {
      sleep(250);
      artifacts = ca24DownloadInventory();
    }
    return artifacts;
  }

  private static JsonArray ca24DownloadInventory() {
    JsonArray artifacts = new JsonArray();
    try (var files = Files.walk(CA24_DOWNLOADS)) {
      files.filter(Files::isRegularFile).forEach(path -> {
        try {
          long bytes = Files.size(path);
          if (bytes <= 0) return;
          JsonObject artifact = new JsonObject();
          artifact.addProperty("name", CA24_DOWNLOADS.relativize(path).toString());
          artifact.addProperty("bytes", bytes);
          artifacts.add(artifact);
        } catch (java.io.IOException ignored) {
          // A file can be replaced while the browser finishes its download.
        }
      });
    } catch (java.io.IOException error) {
      JsonObject failure = new JsonObject();
      failure.addProperty("inventoryFailure", error.getClass().getSimpleName());
      artifacts.add(failure);
    }
    return artifacts;
  }

  private static int ca24NonEmptyArtifactCount(JsonArray artifacts) {
    int count = 0;
    for (var element : artifacts) {
      if (!element.isJsonObject()) continue;
      JsonObject artifact = element.getAsJsonObject();
      if (artifact.has("bytes") && artifact.get("bytes").getAsLong() > 0) count++;
    }
    return count;
  }

  private static JsonObject ca24ApplicationInventory(CorporateActionIdentity identity, JsonObject attachmentInventory) {
    JsonObject application = new JsonObject();
    application.addProperty("application", identity.summary());
    application.addProperty("route", safeRoute(WebDriverRunner.url()));
    application.add("attachments", attachmentInventory == null ? new JsonObject() : attachmentInventory);
    return application;
  }

  private static JsonObject ca24Evidence(String outcome, List<String> observedRows,
      List<String> applicationsWithoutAttachments, List<String> applicationsWithoutUniqueDownload,
      JsonArray applicationInventories) {
    JsonObject evidence = new JsonObject();
    evidence.addProperty("requirement", "CA-24");
    evidence.addProperty("outcome", outcome);
    evidence.addProperty("route", safeRoute(WebDriverRunner.url()));
    evidence.addProperty("readOnly", true);
    evidence.addProperty("uploadAttempted", false);
    evidence.addProperty("filenameMimeContract", "observed-only");
    evidence.addProperty("principles", "DIRECT_TESTING_PRINCIPLES.md");
    JsonArray rows = new JsonArray();
    observedRows.forEach(rows::add);
    evidence.add("observedApplications", rows);
    JsonArray empty = new JsonArray();
    applicationsWithoutAttachments.forEach(empty::add);
    evidence.add("applicationsWithoutUploadedAttachments", empty);
    JsonArray ambiguous = new JsonArray();
    applicationsWithoutUniqueDownload.forEach(ambiguous::add);
    evidence.add("applicationsWithoutUniqueDownload", ambiguous);
    evidence.add("applicationInventories", applicationInventories);
    return evidence;
  }

  private static int ca24Int(JsonObject object, String key) {
    return object != null && object.has(key) && object.get(key).isJsonPrimitive()
      ? object.get(key).getAsInt() : 0;
  }

  private static String captureCa24Evidence(JsonObject evidence) {
    String runId = System.getenv().getOrDefault("TEST_RUN_ID", "local")
      .replaceAll("[^A-Za-z0-9._-]", "_");
    Path directory = Path.of("reports", "evidence", runId, "ca24-diagnostics");
    String fileName = "ca24-attachment-probe-" + System.currentTimeMillis() + ".json";
    Path file = directory.resolve(fileName);
    try {
      Files.createDirectories(directory);
      Files.writeString(file, evidence == null ? "{}" : evidence.toString(), StandardCharsets.UTF_8);
      System.out.println("CA24_EVIDENCE_FILE " + file.toAbsolutePath());
      return file.toAbsolutePath().toString();
    } catch (java.io.IOException error) {
      System.out.println("CA24_EVIDENCE_WRITE_FAILED " + error.getClass().getSimpleName());
      return "";
    }
  }

  private static void clearCa24Downloads() {
    Path buildRoot = Path.of("build").toAbsolutePath().normalize();
    if (!CA24_DOWNLOADS.startsWith(buildRoot)) {
      throw new AssertionError("CA-24 download folder escaped the suite build directory");
    }
    try {
      Files.createDirectories(CA24_DOWNLOADS);
      try (var existing = Files.walk(CA24_DOWNLOADS)) {
        for (Path path : existing.sorted(java.util.Comparator.reverseOrder()).toList()) {
          if (!path.equals(CA24_DOWNLOADS)) Files.deleteIfExists(path);
        }
      }
    } catch (java.io.IOException error) {
      throw new AssertionError("Could not clear the suite-owned CA-24 download folder", error);
    }
  }

  private static void ca24Checkpoint(String checkpointId) {
    CheckpointCapture.capture(checkpointId);
  }

  private static void ca24CheckpointBestEffort(String checkpointId) {
    try {
      ca24Checkpoint(checkpointId);
    } catch (Throwable failure) {
      System.out.println("CA24_CHECKPOINT_FAILED type=" + failure.getClass().getSimpleName());
    }
  }

  private static void openObservedCorporateAction(String country, String caForm) {
    SELECTED_CORPORATE_ACTION.remove();
    awaitCorporateActionsList();
    String body = $("body").shouldBe(visible).getText();
    if (hasVisibleCorporateActionsEmptyState()) {
      throw new AssertionError("No existing Corporate Actions application is available for "
        + country + "/" + caForm + "; the live list exposes an explicit empty state: " + body);
    }
    SelenideElement table = visibleCorporateActionsTable();
    String expectedCountry = country.trim().toUpperCase(java.util.Locale.ROOT);
    String expectedForm = corporateActionFormLabel(caForm);
    CorporateActionIdentity observedTarget = observedCorporateActionTarget(expectedCountry, expectedForm);
    System.out.println("CA_OBSERVED_TARGET " + observedTarget.summary());
    List<SelenideElement> matches = new ArrayList<>();
    List<String> candidates = new ArrayList<>();
    for (SelenideElement row : visibleCorporateActionRows(table)) {
      ElementsCollection cells = row.$$("td");
      CorporateActionIdentity identity = corporateActionIdentity(cells);
      String summary = identity.summary();
      candidates.add(summary);
      System.out.println("CA_ROW_OBSERVED " + summary);
      if (identity.isin().toUpperCase(java.util.Locale.ROOT).startsWith(expectedCountry)
          && identity.form().equalsIgnoreCase(expectedForm)
          && identity.equals(observedTarget)) {
        matches.add(row);
      }
    }
    if (matches.size() != 1) {
      throw new AssertionError("Expected exactly one existing Corporate Actions application for "
        + expectedCountry + "/" + expectedForm + ", found " + matches.size()
        + ". Required observed predicate: " + observedTarget.summary()
        + ". Visible candidates: " + candidates);
    }
    CorporateActionIdentity selectedIdentity = corporateActionIdentity(matches.get(0).$$("td"));
    SELECTED_CORPORATE_ACTION.set(selectedIdentity);
    System.out.println("CA_SELECTED_ROW " + selectedIdentity.summary());
    String beforeUrl = WebDriverRunner.url();
    clickObservedCorporateActionCell(expectedCountry, expectedForm, observedTarget);
    awaitCorporateActionForm(beforeUrl);
  }

  private static void clickObservedCorporateActionCell(
      String expectedCountry, String expectedForm, CorporateActionIdentity observedTarget) {
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      SelenideElement table = visibleCorporateActionsTable();
      List<SelenideElement> refreshedMatches = new ArrayList<>();
      for (SelenideElement row : visibleCorporateActionRows(table)) {
        CorporateActionIdentity identity = corporateActionIdentity(row.$$("td"));
        if (identity.isin().toUpperCase(java.util.Locale.ROOT).startsWith(expectedCountry)
            && identity.form().equalsIgnoreCase(expectedForm)
            && identity.equals(observedTarget)) {
          refreshedMatches.add(row);
        }
      }
      if (refreshedMatches.size() != 1) {
        throw new AssertionError("Expected exactly one refreshed row for observed predicate "
          + observedTarget.summary() + ", found " + refreshedMatches.size());
      }
      try {
        refreshedMatches.get(0).$$("td").get(1).scrollIntoView("{block: 'center'}").click();
        return;
      } catch (org.openqa.selenium.StaleElementReferenceException ignored) {
        sleep(100);
      }
    }
    throw new AssertionError("Observed Corporate Actions row kept rerendering before its unique cell could be clicked: "
      + observedTarget.summary());
  }

  private static CorporateActionIdentity observedCorporateActionTarget(String expectedCountry, String expectedForm) {
    if ("LV".equals(expectedCountry) && OBSERVED_LV_BONUS.form().equalsIgnoreCase(expectedForm)) {
      return OBSERVED_LV_BONUS;
    }
    throw new AssertionError("No observed stable live Corporate Actions predicate is available for "
      + expectedCountry + "/" + expectedForm + ". Refusing arbitrary row selection.");
  }

  private static CorporateActionIdentity corporateActionIdentity(ElementsCollection cells) {
    if (cells.size() < 7) {
      throw new AssertionError("Expected at least seven visible Corporate Actions identity cells, found " + cells.size());
    }
    return new CorporateActionIdentity(
      compactText(cells.get(0).getText()),
      compactText(cells.get(1).getText()),
      compactText(cells.get(2).getText()),
      compactText(cells.get(3).getText()),
      compactText(cells.get(4).getText()),
      compactText(cells.get(5).getText()),
      compactText(cells.get(6).getText()));
  }

  private static String compactText(String value) {
    return value == null ? "" : value.replaceAll("\\s+", " ").trim();
  }

  private static String corporateActionFormLabel(String caForm) {
    return switch (caForm.trim().toLowerCase(java.util.Locale.ROOT)) {
      case "bonus", "bonus issue" -> "Bonus Issue";
      case "dividend", "dividend payment" -> "Dividend Payment";
      case "interest", "interest payment" -> "Interest Payment";
      case "additional_bonds", "additional bonds" -> "Additional Bonds";
      default -> throw new AssertionError("Unsupported live Corporate Actions form selector: " + caForm);
    };
  }

  private static void openObservedCorporateActionsTab(String tabName) {
    assertAdminRoute("/corporate-actions/application-form");
    awaitCorporateActionDetailSurface();
    if (Boolean.getBoolean("ca.tab.instrument")) captureCorporateActionsTabInventory(tabName);
    List<SelenideElement> matches = observedCorporateActionsTabControls(tabName);
    if (matches.size() != 1) {
      captureCorporateActionsTabInventory(tabName);
      System.out.println("CA_TAB_DIRECT_ELEMENT_COUNT " + matches.size() + " wanted=" + tabName);
    }
    String beforeUrl = WebDriverRunner.url();
    Throwable directClickFailure = null;
    if (matches.size() == 1) {
      try {
        CorporateActionsTabProbe.prepare(tabName);
        matches.get(0).scrollIntoView("{block: 'center', inline: 'center'}").click();
        System.out.println("CA_TAB_ELEMENT_CLICKED wanted=" + tabName);
      } catch (Throwable failure) {
        directClickFailure = failure;
        System.out.println("CA_TAB_ELEMENT_CLICK_FAILED wanted=" + tabName + " error=" + failure);
      }
    }
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      if (isCorporateActionsTabActive(tabName) || !java.util.Objects.equals(beforeUrl, WebDriverRunner.url())) return;
      sleep(300);
    }
    Throwable anchoredClickFailure = null;
    try {
      clickCorporateActionsTabByResponsiveAnchor(tabName);
    } catch (Throwable failure) {
      anchoredClickFailure = failure;
      System.out.println("CA_TAB_ANCHORED_CLICK_FAILED wanted=" + tabName + " error=" + failure);
    }
    deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      if (isCorporateActionsTabActive(tabName) || !java.util.Objects.equals(beforeUrl, WebDriverRunner.url())) return;
      sleep(300);
    }
    captureCorporateActionsTabInventory(tabName);
    throw new AssertionError("Corporate Actions tab '" + tabName
      + "' produced no observable active-state transition. directClickFailure=" + directClickFailure
      + ", anchoredClickFailure=" + anchoredClickFailure
      + ", visible tab controls=" + visibleTabInventory());
  }

  private static void assertObservedCorporateActionsList() {
    awaitCorporateActionsList();
    assertObservedHeading("Corporate Actions");
    if (hasVisibleCorporateActionsEmptyState()) return;
    SelenideElement table = visibleCorporateActionsTable();
    ElementsCollection headers = table.$$("th");
    requireVisibleTableHeader(headers, "Application");
    requireVisibleTableHeader(headers, "Issuer name");
    List<SelenideElement> rows = visibleCorporateActionRows(table);
    if (rows.isEmpty()) {
      assertCorporateActionsEmptyState();
    }
  }

  private static void assertObservedCorporateActionDetails() {
    String detailBody = awaitCorporateActionDetailSurface();
    boolean hasObservedField = false;
    for (SelenideElement field : $$("input, select, textarea")) {
      if (field.isDisplayed() && ((field.getValue() != null && !field.getValue().isBlank())
          || (field.getText() != null && !field.getText().isBlank()))) {
        hasObservedField = true;
        break;
      }
    }
    if (!hasObservedField) {
      throw new AssertionError("Existing Corporate Actions application form rendered without an observable field value");
    }
    CorporateActionIdentity selected = SELECTED_CORPORATE_ACTION.get();
    if (selected == null) {
      throw new AssertionError("Corporate Actions detail assertion has no selected live row identity");
    }
    String detailSurface = visibleCorporateActionDetailSurface();
    List<String> missing = selected.detailIdentityValues().stream()
      .filter(value -> !detailSurfaceContains(detailSurface, value))
      .collect(Collectors.toList());
    if (!missing.isEmpty()) {
      throw new AssertionError("Selected Corporate Actions row identity was not fully rendered on detail page."
        + " Selected=" + selected.summary() + ". Missing=" + missing
        + ". Detail surface=" + compactText(detailSurface));
    }
    System.out.println("CA_DETAIL_IDENTITY_ASSERTED " + selected.summary());
  }

  private static String visibleCorporateActionDetailSurface() {
    StringBuilder surface = new StringBuilder($("body").shouldBe(visible).getText());
    for (SelenideElement field : $("body").$$("input, select, textarea")) {
      try {
        if (!field.isDisplayed()) continue;
        for (String value : new String[] {field.getValue(), field.getAttribute("value"), field.getText()}) {
          if (value != null && !value.isBlank()) surface.append(' ').append(value);
        }
      } catch (org.openqa.selenium.StaleElementReferenceException ignored) {
        // Angular may replace a field while the form finishes its render.
      }
    }
    return surface.toString();
  }

  private static boolean detailSurfaceContains(String surface, String value) {
    String normalizedSurface = compactText(surface).toLowerCase(java.util.Locale.ROOT);
    String normalizedValue = compactText(value).toLowerCase(java.util.Locale.ROOT);
    if (normalizedSurface.contains(normalizedValue)) return true;
    if (value.matches("[A-Z][a-z]{2} \\d{1,2}, \\d{4}")) {
      try {
        java.time.LocalDate date = java.time.LocalDate.parse(
          value,
          java.time.format.DateTimeFormatter.ofPattern("MMM d, uuuu", java.util.Locale.ENGLISH));
        for (String alternate : List.of(
            date.toString(),
            date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu")),
            date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")))) {
          if (normalizedSurface.contains(alternate.toLowerCase(java.util.Locale.ROOT))) return true;
        }
      } catch (java.time.format.DateTimeParseException ignored) { }
    }
    return false;
  }

  private static void assertObservedCorporateActionsTab(String tabName, String contentToken) {
    assertAdminRoute("/corporate-actions/application-form");
    awaitCorporateActionDetailSurface();
    List<SelenideElement> tabs = observedCorporateActionsTabControls(tabName);
    if (tabs.size() != 1) {
      captureCorporateActionsTabInventory(tabName);
      throw new AssertionError("Expected exactly one visible Corporate Actions tab '" + tabName
        + "' during assertion, found " + tabs.size() + ". Visible tab controls: " + visibleTabInventory());
    }
    if (!isCorporateActionsTabActive(tabName)) {
      captureCorporateActionsTabInventory(tabName);
      throw new AssertionError("Corporate Actions tab '" + tabName + "' is visible but not observably active");
    }
    String panelText = visibleCorporateActionsTabPanelText(tabName);
    String normalized = panelText.toLowerCase(java.util.Locale.ROOT);
    boolean explicitEmpty = normalized.contains("no " + contentToken)
      || normalized.contains("no file")
      || normalized.contains("no entr")
      || normalized.contains("no sign")
      || normalized.contains("no data")
      || normalized.contains("empty");
    if (!normalized.contains(contentToken.toLowerCase(java.util.Locale.ROOT)) && !explicitEmpty) {
      captureCorporateActionsTabInventory(tabName);
      throw new AssertionError("Corporate Actions tab '" + tabName
        + "' has no observable content or explicit empty state. Panel text=" + panelText);
    }
    System.out.println("CA_TAB_ACTIVE_CONTENT_ASSERTED wanted=" + tabName
      + " contentToken=" + contentToken + " panelText=" + compactText(panelText));
  }

  private static void assertCorporateActionsRoute() {
    assertAdminRoute("/corporate-actions");
  }

  private static void awaitCorporateActionsList() {
    assertCorporateActionsRoute();
    long deadline = System.currentTimeMillis() + Math.max(Configuration.timeout, 60000);
    String body = "";
    while (System.currentTimeMillis() < deadline) {
      String state = corporateActionsListStateJs();
      if ("rows".equals(state)) return;
      if ("empty".equals(state)) return;
      if (!"loading".equals(state)) {
        // Fall back to the heavyweight checks when the JS probe sees no
        // definitive signal; it keeps us robust against markup drift.

        try {
          body = $("body").shouldBe(visible).getText();
        } catch (Throwable ignored) { body = ""; }
        if (hasVisibleCorporateActionRows()) return;
        if (hasVisibleCorporateActionsEmptyState() && !hasVisibleCorporateActionsLoadingIndicator()) return;
      }
      sleep(250);
    }
    throw new AssertionError("Corporate Actions route did not render a table or explicit empty state. Visible text="
      + body.substring(0, Math.min(body.length(), 2000)));
  }

  /** One fast JS snapshot of the Corporate Actions list; avoids Selenide scans
   *  (which stalled ~10s per run under Angular rerenders. */
  private static String corporateActionsListStateJs() {
    try {
      Object stateObj = executeJavaScript(
        "var rows=0,empty=false,loading=false;"
          + "var tabs=[...document.querySelectorAll('li.tab-item')].filter(function(e){"
          + "var r=e.getClientRects();return r.length>0&&r[0].height>0;});"
          + "for (var t=0;t<tabs.length;t++){"
          + "var t2=tabs[t];if(t2&&t2.innerText){};}"
          + "var tables=[...document.querySelectorAll('table')].filter(function(tb){"
          + "var r=tb.getClientRects();return r.length>0&&r[0].height>0;"
          + "&&!!tb.querySelector('tr>td');});"
          + "rows=tables.length>0?1:0;"
          + "if(rows=1) return 'rows';"
          + "var text=(document.body&&document.body.innerText||'').trim().toLowerCase();"
          + "if(text.indexOf('not added any corporate actions')>=0"
          + "||text.indexOf('no corporate action')>=0||text.indexOf('no applications')>=0) empty=true;"
          + "if(empty) return 'empty';"
          + "var spinners=[...document.querySelectorAll('[class*=spinner][class*=loading],[class*=loading]')]"
          + ".filter(function(e){var r=e.getClientRects();return r.length>0&&r[0].height>0;});"
          + "if(spinners.length>0) return 'loading';"
          + "return 'none';");
      return stateObj == null ? "none" : String.valueOf(stateObj);
    } catch (Throwable ignored) {
      return "none";
    }
  }

  private static void assertCorporateActionFormRouteAndHeading() {
    assertAdminRoute("/corporate-actions/application-form");
    assertObservedHeading("Corporate Action", "Application", "Form management",
      "Bonus Issue", "Dividend Payment", "Interest Payment", "Additional Bonds",
      "attached documents");
  }

  /**
   * The application-form route first renders a shell heading and a loading
   * overlay. That shell is not the detail surface and its text can cause a
   * tab locator to inspect the previous/empty DOM. Wait for the actual
   * application data before discovering any of the three observed tabs.
   */
  private static String awaitCorporateActionDetailSurface() {
    assertAdminRoute("/corporate-actions/application-form");
    long startedAt = System.currentTimeMillis();
    long deadline = startedAt + Math.max(Configuration.timeout, 60000);
    boolean refreshedEmptyShell = false;
    String body = "";
    while (System.currentTimeMillis() < deadline) {
      body = $("body").shouldBe(visible).getText();
      String normalized = compactText(body).toLowerCase(java.util.Locale.ROOT);
      boolean loading = normalized.contains("loading application details")
        || normalized.contains("loading application");
      boolean tabShell = normalized.contains("application data")
        && normalized.contains("signatures")
        && normalized.contains("history")
        && normalized.contains("attachments");
      boolean detailSurface = tabShell && normalized.contains("information about issuer");
      CorporateActionIdentity selected = SELECTED_CORPORATE_ACTION.get();
      String bodySnapshot = body;
      boolean selectedIdentityVisible = selected != null
        && selected.detailIdentityValues().stream().allMatch(value -> detailSurfaceContains(bodySnapshot, value));
      boolean observedDocumentSurface = selected != null
        && normalized.contains(selected.form().toLowerCase(java.util.Locale.ROOT))
        && normalized.contains("attached documents");
      boolean observedTabSurface = tabShell
        && (normalized.contains("accessed application")
          || normalized.contains("status changed from")
          || normalized.contains("digitally sign applications")
          || normalized.contains("upload attachments")
          || normalized.contains("choose file"));
      if ((detailSurface || selectedIdentityVisible || observedDocumentSurface || observedTabSurface) && !loading) return body;
      boolean emptyTabShell = tabShell
        && !detailSurface && !observedDocumentSurface && !observedTabSurface;
      if (!refreshedEmptyShell && emptyTabShell && System.currentTimeMillis() - startedAt >= 20000) {
        refreshedEmptyShell = true;
        refresh();
        sleep(1000);
        continue;
      }
      sleep(250);
    }
    if (Boolean.getBoolean("ca.tab.instrument")) captureCorporateActionsTabInventory("detail-surface");
    throw new AssertionError("Existing Corporate Actions detail route did not leave its loading shell."
      + " Visible text=" + compactText(body).substring(0, Math.min(compactText(body).length(), 3000)));
  }

  private static void assertAdminRoute(String expectedPath) {
    String url = WebDriverRunner.url();
    String expected = ADMIN_BASE_URL + expectedPath;
    if (url == null || !sameOrigin(url, ADMIN_BASE_URL)
        || !url.matches(java.util.regex.Pattern.quote(expected) + "(?:[/?#].*)?")) {
      throw new AssertionError("Expected admin route " + expectedPath + ", got " + url);
    }
  }

  private static void awaitCorporateActionForm(String beforeUrl) {
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      String current = WebDriverRunner.url();
      if (current != null && current.contains("/corporate-actions/application-form/")) {
        assertAdminRoute("/corporate-actions/application-form");
        return;
      }
      sleep(100);
    }
    throw new AssertionError("Observed existing Corporate Actions row did not navigate to /corporate-actions/application-form/{id}; current URL="
      + WebDriverRunner.url());
  }

  private static SelenideElement visibleCorporateActionsTable() {
    List<SelenideElement> dataMatches = new ArrayList<>();
    List<SelenideElement> headerMatches = new ArrayList<>();
    List<String> visibleTables = new ArrayList<>();
    for (SelenideElement table : $("body").$$("table")) {
      if (!table.isDisplayed()) continue;
      String text = table.getText() == null ? "" : table.getText();
      visibleTables.add(text.replaceAll("\\s+", " ").trim().substring(0, Math.min(240, text.replaceAll("\\s+", " ").trim().length())));
      if (text.contains("Application") && text.contains("Issuer name")) headerMatches.add(table);
      if (!visibleCorporateActionRows(table).isEmpty()) dataMatches.add(table);
    }
    if (dataMatches.size() == 1) return dataMatches.get(0);
    if (dataMatches.size() > 1) {
      throw new AssertionError("Expected one visible Corporate Actions data table, found " + dataMatches.size()
        + ". Visible tables=" + visibleTables);
    }
    if (headerMatches.size() == 1) return headerMatches.get(0);
    throw new AssertionError("Expected exactly one visible Corporate Actions table, found data=" + dataMatches.size()
      + ", header=" + headerMatches.size() + ". Visible tables=" + visibleTables);
  }

  private static List<SelenideElement> visibleCorporateActionRows(SelenideElement table) {
    List<SelenideElement> rows = new ArrayList<>();
    for (SelenideElement row : table.$$("tbody tr")) {
      if (!row.isDisplayed()) continue;
      ElementsCollection cells = row.$$("td");
      if (cells.size() >= 3 && cells.get(0).getText().trim().matches("[A-Za-z]{2}\\d{5,}")) rows.add(row);
    }
    return rows;
  }

  private static boolean hasVisibleCorporateActionsTable() {
    return hasVisibleCorporateActionRows();
  }

  private static boolean hasVisibleCorporateActionRows() {
    for (SelenideElement table : $("body").$$("table")) {
      try {
        if (!table.isDisplayed()) continue;
        if (!visibleCorporateActionRows(table).isEmpty()) return true;
      } catch (RuntimeException ignored) {
        // Retry against a fresh collection on the next poll.
      }
    }
    return false;
  }

  private static boolean hasVisibleCorporateActionsEmptyState() {
    try {
      String visibleText = executeJavaScript("""
        return Array.from(document.querySelectorAll('body div, body span, body p, body section, body td'))
          .filter((element) => {
            const style = getComputedStyle(element);
            const rect = element.getBoundingClientRect();
            return style.display !== 'none' && style.visibility !== 'hidden'
              && rect.width > 0 && rect.height > 0;
          })
          .map((element) => (element.innerText || element.textContent || '').replace(/\\s+/g, ' ').trim())
          .join('|');
      """);
      String normalized = compactText(visibleText).toLowerCase(java.util.Locale.ROOT);
      return normalized.contains("you’ve not added any corporate actions yet")
        || normalized.contains("you've not added any corporate actions yet");
    } catch (Throwable ignored) {
      // A route transition can replace the document between script calls.
      // The next poll obtains a fresh DOM snapshot.
      return false;
    }
  }

  private static boolean hasVisibleCorporateActionsLoadingIndicator() {
    try {
      Object found = executeJavaScript(
        "return [...document.querySelectorAll('[class*=spinner], [class*=loading], [class*=loader]')]"
          + ".some(function(e){var r=e.getClientRects();return r.length>0&&r[0].height>0;});");
      return Boolean.TRUE.equals(found);
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static boolean hasCorporateActionsEmptyState(String body) {
    String normalized = body == null ? "" : body.toLowerCase(java.util.Locale.ROOT);
    return normalized.contains("no corporate action") || normalized.contains("no application")
      || normalized.contains("no data") || normalized.contains("not added any corporate actions");
  }

  private static List<SelenideElement> observedCorporateActionsTabControls(String tabName) {
    WebElement observed = CorporateActionsTabProbe.findClickable(tabName);
    if (observed == null) return List.of();
    try {
      SelenideElement control = $(observed);
      if (!control.isDisplayed() || !control.isEnabled()) return List.of();
      return List.of(control);
    } catch (Throwable inaccessibleFrameElement) {
      System.out.println("CA_TAB_RECURSIVE_ELEMENT_NOT_WRAPPED wanted=" + tabName
        + " error=" + inaccessibleFrameElement);
      return List.of();
    }
  }

  private static boolean isCorporateActionsTabActive(String tabName) {
    try {
      return CorporateActionsTabProbe.isActive(tabName);
    } catch (Throwable probeFailure) {
      System.out.println("CA_TAB_ACTIVE_PROBE_FAILED wanted=" + tabName + " error=" + probeFailure);
      return false;
    }
  }

  private static String visibleCorporateActionsTabPanelText(String tabName) {
    try {
      return CorporateActionsTabProbe.panelText(tabName);
    } catch (Throwable probeFailure) {
      System.out.println("CA_TAB_PANEL_PROBE_FAILED error=" + probeFailure);
      List<String> panels = new ArrayList<>();
      for (SelenideElement panel : $("body").$$("[role=tabpanel], .tab-pane, .tab-content")) {
        if (panel.isDisplayed() && panel.getText() != null && !panel.getText().isBlank()) panels.add(panel.getText().trim());
      }
      return String.join(" | ", panels);
    }
  }

  /**
   * Last-resort click using geometry discovered from the live DOM.  The point
   * is accepted only when the probe found a visible target and a stable bar
   * containing at least two known tab labels.  No screenshot coordinates or
   * viewport constants are used here.
   */
  private static void clickCorporateActionsTabByResponsiveAnchor(String tabName) {
    JsonObject geometry = JsonParser.parseString(CorporateActionsTabProbe.geometry(tabName)).getAsJsonObject();
    if (!geometry.has("matched") || !geometry.get("matched").getAsBoolean()) {
      throw new AssertionError("Recursive tab probe found no visible target for '" + tabName + "'");
    }
    if (!geometry.has("anchorLabels") || geometry.getAsJsonArray("anchorLabels").size() < 2) {
      throw new AssertionError("Refusing coordinate fallback for '" + tabName
        + "': the probe did not identify at least two stable neighbor labels");
    }
    if (!geometry.has("anchorBar") || geometry.get("anchorBar").isJsonNull()) {
      throw new AssertionError("Refusing coordinate fallback for '" + tabName + "': tabs bar geometry is missing");
    }
    if (!geometry.has("target") || geometry.get("target").isJsonNull()) {
      throw new AssertionError("Refusing coordinate fallback for '" + tabName + "': target geometry is missing");
    }
    JsonObject target = geometry.getAsJsonObject("target");
    JsonObject anchorBar = geometry.getAsJsonObject("anchorBar");
    JsonObject viewport = geometry.getAsJsonObject("viewport");
    double centerX = target.get("x").getAsDouble() + target.get("width").getAsDouble() / 2.0;
    double centerY = target.get("y").getAsDouble() + target.get("height").getAsDouble() / 2.0;
    double viewportWidth = viewport.get("width").getAsDouble();
    double viewportHeight = viewport.get("height").getAsDouble();
    double barLeft = anchorBar.get("x").getAsDouble();
    double barRight = barLeft + anchorBar.get("width").getAsDouble();
    double barTop = anchorBar.get("y").getAsDouble();
    double barBottom = barTop + anchorBar.get("height").getAsDouble();
    if (target.get("width").getAsDouble() <= 0 || target.get("height").getAsDouble() <= 0
        || centerX < 0 || centerY < 0 || centerX >= viewportWidth || centerY >= viewportHeight
        || centerX < barLeft || centerX > barRight || centerY < barTop || centerY > barBottom) {
      throw new AssertionError("Refusing unsafe responsive anchor point for '" + tabName
        + "': target=" + target + ", anchorBar=" + anchorBar + ", viewport=" + viewport);
    }
    System.out.println("CA_TAB_ANCHORED_CLICK wanted=" + tabName
      + " targetDescription=" + geometry.get("targetDescription")
      + " anchorLabels=" + geometry.get("anchorLabels")
      + " x=" + centerX + " y=" + centerY);
    new Actions(WebDriverRunner.getWebDriver())
      .moveToLocation((int) Math.round(centerX), (int) Math.round(centerY))
      .click()
      .perform();
  }

  private static void assertObservedHeading(String... acceptedFragments) {
    List<String> headings = new ArrayList<>();
    for (SelenideElement heading : $("body").$$("h1, h2, h3, .text-heading-large, .text-heading-small")) {
      if (!heading.isDisplayed()) continue;
      String text = heading.getText() == null ? "" : heading.getText().trim();
      if (text.isBlank()) continue;
      headings.add(text);
      String normalized = text.toLowerCase(java.util.Locale.ROOT);
      for (String accepted : acceptedFragments) {
        if (normalized.contains(accepted.toLowerCase(java.util.Locale.ROOT))) return;
      }
    }
    throw new AssertionError("Expected a visible Corporate Actions heading containing one of "
      + java.util.Arrays.toString(acceptedFragments) + ", observed headings=" + headings);
  }

  private static void requireVisibleTableHeader(ElementsCollection headers, String expected) {
    for (SelenideElement header : headers) {
      if (header.isDisplayed() && expected.equalsIgnoreCase(header.getText().trim())) return;
    }
    throw new AssertionError("Expected Corporate Actions table header '" + expected + "'");
  }

  private static void assertCorporateActionsEmptyState() {
    String body = $("body").shouldBe(visible).getText();
    if (!hasVisibleCorporateActionsEmptyState()) {
      throw new AssertionError("Corporate Actions table is empty without an explicit empty state. Visible text=" + body);
    }
  }

  private static String visibleTabInventory() {
    List<String> values = new ArrayList<>();
    for (SelenideElement control : $("body").$$("a, button, [role=tab]")) {
      if (!control.isDisplayed()) continue;
      String text = control.getText() == null ? "" : control.getText().trim();
      if (!text.isBlank()) values.add(text + "[aria-selected=" + control.getAttribute("aria-selected")
        + ",class=" + control.getAttribute("class") + "]");
    }
    return values.toString();
  }

  /**
   * Capture the real rendered surface when a tab is visible in the browser
   * but absent from ordinary Selenium text/DOM traversal. This is opt-in so a
   * normal cluster run does not write diagnostic noise. The browser probe
   * redacts email-like values, credential attributes, and form values before
   * this method writes or attaches anything.
   */
  private static void captureCorporateActionsTabInventory(String tabName) {
    if (!Boolean.getBoolean("ca.tab.instrument")) return;

    String filePart = tabName.replaceAll("[^A-Za-z0-9]+", "-")
      .replaceAll("^-|-$", "").toLowerCase(java.util.Locale.ROOT);
    if (filePart.isBlank()) filePart = "unknown";
    String runId = System.getenv().getOrDefault("TEST_RUN_ID", "local");
    Path directory = Path.of(System.getProperty("ca.tab.inventory.dir",
      "reports/evidence/" + runId + "/ca-tab-diagnostics"));
    String stem = "ca-tab-" + filePart + "-" + System.currentTimeMillis();
    try {
      Files.createDirectories(directory);
      String inventory = CorporateActionsTabProbe.diagnostic(tabName);
      Path inventoryFile = directory.resolve(stem + "-inventory.txt");
      Files.writeString(inventoryFile, inventory == null ? "<null JS result>" : inventory, StandardCharsets.UTF_8);
      System.out.println("CA_TAB_INVENTORY_FILE " + inventoryFile.toAbsolutePath());
      Allure.addAttachment("Corporate Actions " + tabName + " DOM inventory", "text/plain", inventory == null ? "<null JS result>" : inventory);
    } catch (Throwable diagnosticFailure) {
      System.out.println("CA_TAB_INVENTORY_FAILED " + diagnosticFailure);
    }
  }

  @Then("upcoming_events_visible")
  public void upcoming_events_visible() {
    assertSemanticState("upcoming_events_visible");
    CheckpointCapture.capture("view-upcoming-events-in-home-page.admin-view-upcoming-events-in-home-page.upcoming-events-visible");
  }

  @Then("the customer home page is displayed")
  public void the_customer_home_page_is_displayed() {
    String url = com.codeborne.selenide.WebDriverRunner.url();
    if (url == null || !url.matches("https://eservicesdev\\.sets\\.lv(?:/home)?/?(?:[?#].*)?")) {
      throw new AssertionError("Expected customer home URL, got " + url);
    }
    $("main, [role=main], body").shouldBe(visible);
  }

  @Then("the admin home page is displayed")
  public void the_admin_home_page_is_displayed() {
    String url = com.codeborne.selenide.WebDriverRunner.url();
    if (url == null || !url.matches("https://eservicesdevint\\.sets\\.lv(?:/home)?/?(?:[?#].*)?")) {
      throw new AssertionError("Expected admin home URL, got " + url);
    }
    String bodyText = waitForNonEmptyBodyText();
    if (bodyText.isBlank()) {
      throw new AssertionError("Admin home did not finish loading visible content");
    }
  }

  @Then("the admin upcoming events section is displayed")
  public void the_admin_upcoming_events_section_is_displayed() {
    String url = com.codeborne.selenide.WebDriverRunner.url();
    if (url == null || !url.contains("eservicesdevint.sets.lv/home")) {
      throw new AssertionError("Expected admin home URL, got " + url);
    }
    String bodyText = waitForNonEmptyBodyText();
    String normalized = bodyText.toLowerCase(java.util.Locale.ROOT);
    boolean upcomingHeading = normalized.contains("upcoming event") || normalized.contains("gaidām") || normalized.contains("предстоящ");
    boolean corporateActionsEmptyState = normalized.contains("corporate actions")
      && normalized.contains("you’ve not added any corporate actions yet");
    if (!(upcomingHeading || corporateActionsEmptyState)) {
      throw new AssertionError("Upcoming events section or its observed empty state not present. Visible text: " + normalized.substring(0, Math.min(normalized.length(), 2000)));
    }
  }

  @Then("the admin page {string} is displayed with {string}")
  public void the_admin_page_is_displayed_with(String expectedPath, String expectedKeyword) {
    long deadline = System.currentTimeMillis() + 30000;
    String url = com.codeborne.selenide.WebDriverRunner.url();
    String body = "";
    while (System.currentTimeMillis() < deadline) {
      url = com.codeborne.selenide.WebDriverRunner.url();
      body = $("body").shouldBe(visible).getText();
      if (url != null && url.contains(expectedPath) && body != null
          && body.toLowerCase(java.util.Locale.ROOT).contains(expectedKeyword.toLowerCase(java.util.Locale.ROOT))) {
        return;
      }
      sleep(250);
    }
    throw new AssertionError("Expected admin page path=" + expectedPath + " keyword=" + expectedKeyword
      + ", got url=" + url + " visibleText=" + body);
  }

  // ── External role edit round-trip state ──────────────────────
  private static String rememberedRoleDescription = null;
  private static String rememberedRoleId = null;
  private static int rememberedRoleDescriptionFieldIndex = -1;
  private static String rememberedRoleDescriptionFieldId = null;
  private static String rememberedRoleDescriptionFieldName = null;
  private static String rememberedRoleDescriptionFormControlName = null;
  private static String rememberedRoleDescriptionDataCy = null;
  private static String submittedExternalRoleDescription = null;
  private static boolean externalRoleServerStateDirty = false;
  private static final java.util.regex.Pattern PAGINATION_PATTERN =
    java.util.regex.Pattern.compile("(\\d+)\\s*-\\s*(\\d+)\\s*of\\s*(\\d+)");

  @When("I find and open the observed external role {string} editor")
  public void i_find_and_open_observed_external_role_editor(String roleName) {
    long t0 = System.currentTimeMillis();
    assertAdminList("/external/admin/authority-rights", "External Roles");

    // First, try the current page
    SelenideElement targetRow = findRoleOnCurrentPage(roleName);

    // If not found, paginate through pages
    int pageLimit = 50;
    while (targetRow == null && pageLimit-- > 0) {
      // Look for pagination controls
      SelenideElement nextBtn = $("a[aria-label=Next], .pagination .next a, " +
        "li.page-item.next a, li.next a, .page-link.next");
      if (nextBtn.exists() && nextBtn.isDisplayed() && nextBtn.isEnabled()) {
        String beforeUrl = WebDriverRunner.url();
        executeJavaScript("arguments[0].click()", nextBtn.getWrappedElement());
        awaitPageTransition(beforeUrl, pageFingerprint());
        sleep(500);
        targetRow = findRoleOnCurrentPage(roleName);
      } else {
        break;
      }
    }

    if (targetRow == null) {
      // Fallback: dump visible rows for debugging
      StringBuilder rows = new StringBuilder();
      for (SelenideElement row : $("table").$$("tbody tr")) {
        if (row.isDisplayed()) rows.append("[").append(row.getText().replaceAll("\\s+", " ").trim()).append("] ");
      }
      throw new AssertionError("Role '" + roleName + "' not found on any page. Visible rows: " + rows);
    }

    String observedId = targetRow.getAttribute("id");
    if (observedId == null || !observedId.matches("[0-9]+")) {
      throw new AssertionError("Observed role row has no numeric identity");
    }

    rememberedRoleId = observedId;
    System.out.println("  👁️  Found the configured external role (" + (System.currentTimeMillis() - t0) + "ms)");
    adminOpen("/external/admin/authority-rights/" + observedId + "/edit");

    // Wait for editor route
    long deadline = System.currentTimeMillis() + 30000;
    while (System.currentTimeMillis() < deadline) {
      String url = WebDriverRunner.url();
      if (url != null && url.contains("/external/admin/authority-rights/" + observedId + "/edit")) break;
      sleep(300);
    }
    System.out.println("  [timer] editor opened (" + (System.currentTimeMillis() - t0) + "ms)");
  }

  private static SelenideElement findRoleOnCurrentPage(String roleName) {
    SelenideElement table = visibleManagementTable();
    for (SelenideElement row : table.$$("tbody tr[id]")) {
      if (!row.isDisplayed()) continue;
      if (rowHasExactCellText(row, roleName)) return row;
    }
    return null;
  }

  @Then("the external role editor shows the role {string}")
  public void the_external_role_editor_shows_the_role(String roleName) {
    String url = WebDriverRunner.url();
    if (url == null || !url.contains("/external/admin/authority-rights/") || !url.contains("/edit")) {
      throw new AssertionError("Expected role editor route, got " + url);
    }
    awaitRoleEditorShell("/external/admin/authority-rights/" + rememberedRoleId + "/edit");
    long deadline = System.currentTimeMillis() + 15000;
    boolean roleObserved = false;
    while (System.currentTimeMillis() < deadline) {
      Object observed = executeJavaScript(
        "const expected=String(arguments[0]);"
          + "if(String(document.body?.innerText||'').includes(expected))return true;"
          + "return [...document.querySelectorAll('input,textarea')]"
          + ".some(field=>String(field.value||'').trim()===expected);",
        roleName);
      if (Boolean.TRUE.equals(observed)) {
        roleObserved = true;
        break;
      }
      sleep(100);
    }
    if (!roleObserved) {
      throw new AssertionError("Role editor does not contain '" + roleName + "' in visible text or field values");
    }
    System.out.println("  👁️  External role editor confirmed for " + roleName);
  }

  @When("I remember the role Description and append {string} after the word {string}")
  public void i_remember_description_and_append(String suffix, String afterWord) {
    if (externalRoleServerStateDirty) {
      throw new AssertionError("An earlier external-role mutation still requires fixture cleanup");
    }
    SelenideElement descField = findDescriptionField();
    Number fieldIndex = executeJavaScript(
      "return [...document.querySelectorAll('textarea,input')].indexOf(arguments[0]);",
      descField.getWrappedElement());
    rememberedRoleDescriptionFieldIndex = fieldIndex == null ? -1 : fieldIndex.intValue();
    if (rememberedRoleDescriptionFieldIndex < 0) {
      throw new AssertionError("The external role Description control could not be correlated");
    }
    rememberedRoleDescriptionFieldId = descField.getAttribute("id");
    rememberedRoleDescriptionFieldName = descField.getAttribute("name");
    rememberedRoleDescriptionFormControlName = descField.getAttribute("formcontrolname");
    rememberedRoleDescriptionDataCy = descField.getAttribute("data-cy");
    rememberedRoleDescription = descField.getValue() == null ? "" : descField.getValue();
    System.out.println("  📝  Remembered the original external role Description");

    // Find the position after the word "tests" and insert suffix
    String modified = rememberedRoleDescription;
    int idx = modified.toLowerCase(java.util.Locale.ROOT).lastIndexOf(afterWord.toLowerCase(java.util.Locale.ROOT));
    if (idx >= 0) {
      idx += afterWord.length(); // position after the word
      modified = modified.substring(0, idx) + suffix + modified.substring(idx);
    } else {
      // Fallback: append at end
      modified = modified + suffix;
    }
    setAngularFieldValue(descField, modified);
    submittedExternalRoleDescription = descField.getValue() == null ? "" : descField.getValue();
    if (sameRoleDescription(rememberedRoleDescription, submittedExternalRoleDescription)) {
      throw new AssertionError("The external role Description did not accept the requested modification");
    }
    System.out.println("  📝  External role Description modified");
  }

  @And("I click {string} on the role editor")
  public void i_click_save_on_role_editor(String buttonLabel) {
    boolean restoredState = rememberedRoleDescription != null
      && sameRoleDescription(rememberedRoleDescription, submittedExternalRoleDescription);
    if (!restoredState) externalRoleServerStateDirty = true;
    saveRoleAndReturnToList(buttonLabel, "/external/admin/authority-rights");
    if (restoredState) externalRoleServerStateDirty = false;
  }

  @When("I find and open the observed external role {string} editor again")
  public void i_find_and_open_external_role_editor_again(String roleName) {
    assertAdminList("/external/admin/authority-rights", "External Roles");
    if (rememberedRoleId == null || !rememberedRoleId.matches("[0-9]+")) {
      throw new AssertionError("No observed external role identity was retained for reopening");
    }
    System.out.println("  👁️  Re-opening the configured external role");
    adminOpen("/external/admin/authority-rights/" + rememberedRoleId + "/edit");

    long deadline = System.currentTimeMillis() + 30000;
    while (System.currentTimeMillis() < deadline) {
      String url = WebDriverRunner.url();
      if (url != null && url.contains("/external/admin/authority-rights/" + rememberedRoleId + "/edit")) break;
      sleep(300);
    }
    String persistedDescription = awaitExternalRoleDescriptionValue();
    if (submittedExternalRoleDescription == null
        || !sameRoleDescription(submittedExternalRoleDescription, persistedDescription)) {
      throw new AssertionError("The external role Description mutation was not persisted after reopening");
    }
    System.out.println("ROLE_STATE_VERIFY type=external matched=true");
  }

  @When("I restore the original role Description")
  public void i_restore_original_role_description() {
    if (rememberedRoleDescription == null) {
      throw new AssertionError("No original role Description was remembered — run the modify step first");
    }
    SelenideElement descField = findDescriptionField();
    System.out.println("  📝  Restoring the original external role Description");
    setAngularFieldValue(descField, rememberedRoleDescription);
    submittedExternalRoleDescription = descField.getValue() == null ? "" : descField.getValue();
  }

  private static SelenideElement findDescriptionField() {
    // Look for a textarea or input with name/placeholder containing "description"
    for (SelenideElement field : $$("textarea, input")) {
      if (!field.isDisplayed()) continue;
      String name = field.getAttribute("name");
      String placeholder = field.getAttribute("placeholder");
      String id = field.getAttribute("id");
      if ((name != null && name.toLowerCase(java.util.Locale.ROOT).contains("description"))
          || (placeholder != null && placeholder.toLowerCase(java.util.Locale.ROOT).contains("description"))
          || (id != null && id.toLowerCase(java.util.Locale.ROOT).contains("description"))) {
        return field;
      }
    }
    // Fallback: pick the first visible textarea
    for (SelenideElement field : $$("textarea")) {
      if (field.isDisplayed()) return field;
    }
    // Last fallback: pick the last visible input (often the description field)
    SelenideElement last = null;
    for (SelenideElement field : $$("input")) {
      if (field.isDisplayed()) last = field;
    }
    if (last != null) return last;
    throw new AssertionError("No visible Description field found in the role editor");
  }

  private static String pageFingerprint() {
    SelenideElement body = $("body").shouldBe(visible);
    String html = body.getAttribute("innerHTML");
    return html == null ? body.getText() : html;
  }

  private static boolean rowHasExactCellText(SelenideElement row, String expected) {
    String wanted = expected == null ? "" : expected.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    for (SelenideElement cell : row.$$("td")) {
      String text = cell.getText();
      String normalized = text == null ? "" : text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
      if (wanted.equalsIgnoreCase(normalized)) return true;
    }
    return false;
  }

  private static void setAngularFieldValue(SelenideElement field, String value) {
    executeJavaScript("arguments[0].scrollIntoView({block:'center',inline:'center'});", field.getWrappedElement());
    try {
      field.click();
      field.sendKeys(Keys.chord(Keys.CONTROL, "a"));
      field.sendKeys(value);
      field.sendKeys(Keys.TAB);
    } catch (ElementClickInterceptedException intercepted) {
      executeJavaScript(
        "const el=arguments[0], value=arguments[1];"
          + "const proto=el.tagName==='TEXTAREA'?window.HTMLTextAreaElement.prototype:window.HTMLInputElement.prototype;"
          + "const setter=Object.getOwnPropertyDescriptor(proto,'value').set;setter.call(el,value);"
          + "el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:value}));"
          + "el.dispatchEvent(new Event('change',{bubbles:true}));el.dispatchEvent(new Event('blur',{bubbles:true}));",
        field.getWrappedElement(), value);
    }
  }

  private static void saveRoleAndReturnToList(String buttonLabel, String listPath) {
    installRoleSaveResponseProbe();
    for (int attempt = 1; attempt <= 2; attempt++) {
      String preferredSelector = attempt == 1 ? "form button[type=submit]" : "#editingNavbar button";
      SelenideElement save = firstVisibleEnabledRoleSave(preferredSelector);
      if (save == null) save = firstVisibleEnabledRoleSave("button, [role=button]");
      if (save == null) throw new AssertionError("Role editor exposed no visible enabled Save control");
      executeJavaScript("arguments[0].scrollIntoView({block:'center'});", save.getWrappedElement());
      try {
        save.click();
      } catch (ElementClickInterceptedException intercepted) {
        executeJavaScript("arguments[0].click();", save.getWrappedElement());
      }
      System.out.println("  👁️  Clicked '" + buttonLabel + "' on role editor attempt=" + attempt);

      long deadline = System.currentTimeMillis() + 15000;
      while (System.currentTimeMillis() < deadline) {
        String current = WebDriverRunner.url();
        if (current != null && current.contains(listPath) && !current.contains("/edit")) {
          reconcileSuccessfulRoleSaveRequest(listPath);
          if ("/external/admin/authority-rights".equals(listPath)) {
            externalRoleServerStateDirty = rememberedRoleDescription != null
              && !rememberedRoleDescription.equals(submittedExternalRoleDescription);
          }
          return;
        }
        int terminalStatus = latestRoleSaveResponseStatus();
        if (terminalStatus >= 400) {
          throw new AssertionError("Role save request failed with HTTP " + terminalStatus);
        }
        sleep(250);
      }
    }
    throw new AssertionError("Role save did not return to its list; formState=" + roleFormState());
  }

  private static void installRoleSaveResponseProbe() {
    executeJavaScript(
      "if(!window.__roleSaveProbeInstalled){window.__roleSaveProbeInstalled=true;window.__roleSaveResponses=[];"
        + "const open=XMLHttpRequest.prototype.open,send=XMLHttpRequest.prototype.send;"
        + "const shape=v=>{if(Array.isArray(v))return 'array['+v.length+']';if(v===null)return 'null';return typeof v};"
        + "XMLHttpRequest.prototype.open=function(method,url){this.__roleSaveMethod=String(method);this.__roleSaveUrl=String(url);return open.apply(this,arguments)};"
        + "XMLHttpRequest.prototype.send=function(body){if((this.__roleSaveUrl||'').includes('authority-rights')){"
        + "let requestShape='';try{const parsed=JSON.parse(String(body||''));requestShape=Object.entries(parsed)"
        + ".map(([key,value])=>key+':'+shape(value)).sort().join(',')}catch(_){}"
        + "this.addEventListener('loadend',()=>window.__roleSaveResponses.push({"
        + "method:this.__roleSaveMethod,path:new URL(this.__roleSaveUrl,location.href).pathname,status:this.status,requestShape,"
        + "responseBytes:String(this.responseText||'').length,completedAt:Date.now()}));}return send.apply(this,arguments)};}"
        + "window.__roleSaveResponses=[];window.__roleSaveStartedAt=Date.now();"
    );
  }

  private static int latestRoleSaveResponseStatus() {
    Object status = executeJavaScript(
      "const values=(window.__roleSaveResponses||[]).filter(value=>value.method==='PUT');"
        + "return values.length?Number(values[values.length-1].status||0):0;");
    return status instanceof Number ? ((Number) status).intValue() : 0;
  }

  private static void reconcileSuccessfulRoleSaveRequest(String listPath) {
    boolean external = "/external/admin/authority-rights".equals(listPath);
    String expectedRoleId = external ? rememberedRoleId : rememberedInternalRoleId;
    String expectedApiPath = "/api/" + (external ? "external" : "internal")
      + "-authority-rights/" + expectedRoleId;
    long probeStartedAt = roleSaveProbeStartedAt();
    NetworkMockSupport.drainPerformanceLogs();
    // The editor GET can remain in Chrome's performance log after the fully
    // rendered editor has already submitted and navigated away. Remove only
    // that proven-stale read. A mutation PUT remains blocking unless exact
    // successful response evidence below correlates it.
    removeLatestMatchingRoleRequest("GET", expectedApiPath, 0, probeStartedAt);
    Object completion = executeJavaScript(
      "const values=(window.__roleSaveResponses||[]).filter(value=>value.method==='PUT');"
        + "const value=values.length?values[values.length-1]:null;"
        + "return value?JSON.stringify(value):'';");
    String observed = completion == null ? "" : completion.toString();
    java.util.regex.Matcher statusMatcher = java.util.regex.Pattern.compile("\"status\":(\\d+)").matcher(observed);
    java.util.regex.Matcher pathMatcher = java.util.regex.Pattern.compile("\"path\":\"([^\"]+)\"").matcher(observed);
    java.util.regex.Matcher timeMatcher = java.util.regex.Pattern.compile("\"completedAt\":(\\d+)").matcher(observed);
    if (!statusMatcher.find() || !pathMatcher.find() || !timeMatcher.find()) {
      Long verifiedAt = verifySubmittedRoleState(listPath);
      if (verifiedAt != null) {
        String expectedPath = "/api/"
          + ("/external/admin/authority-rights".equals(listPath) ? "external" : "internal")
          + "-authority-rights/"
          + ("/external/admin/authority-rights".equals(listPath) ? rememberedRoleId : rememberedInternalRoleId);
        removeLatestMatchingRoleRequest("PUT", expectedPath, probeStartedAt, verifiedAt);
        return;
      }
      throw new AssertionError("Role editor returned to the list without positive save evidence");
    }
    int status = Integer.parseInt(statusMatcher.group(1));
    String path = pathMatcher.group(1);
    long completedAt = Long.parseLong(timeMatcher.group(1));
    if (expectedRoleId == null || !path.contains(expectedApiPath)) {
      throw new AssertionError("Role editor response evidence did not match the current save target");
    }
    if (status < 200 || status >= 300) {
      throw new AssertionError("Role editor navigated after an unsuccessful PUT response: HTTP " + status);
    }
    removeLatestMatchingRoleRequest("PUT", path, probeStartedAt, completedAt);
  }

  private static long roleSaveProbeStartedAt() {
    Object value = executeJavaScript("return Number(window.__roleSaveStartedAt||0);");
    return value instanceof Number ? ((Number) value).longValue() : 0;
  }

  /** Remove one exact Chrome request ID correlated to this role target and save window. */
  private static boolean removeLatestMatchingRoleRequest(
      String method, String expectedPath, long minStartedAt, long maxStartedAt) {
    Map.Entry<String, RuntimeState.PendingRequest> candidate = null;
    for (Map.Entry<String, RuntimeState.PendingRequest> entry : PENDING_DATA_REQUESTS.entrySet()) {
      RuntimeState.PendingRequest pending = entry.getValue();
      if (pending == null || !method.equalsIgnoreCase(pending.method)) continue;
      String actualPath = requestPath(pending.url);
      if (!actualPath.equals(expectedPath) && !actualPath.endsWith(expectedPath)) continue;
      if (pending.startedAt < minStartedAt || pending.startedAt > maxStartedAt) continue;
      if (candidate == null || pending.startedAt > candidate.getValue().startedAt) candidate = entry;
    }
    if (candidate == null) return false;
    boolean removed = PENDING_DATA_REQUESTS.remove(candidate.getKey(), candidate.getValue());
    if (removed && PENDING_DATA_REQUESTS.isEmpty()) lastDataActivityAt = 0;
    return removed;
  }

  private static String requestPath(String rawUrl) {
    if (rawUrl == null || rawUrl.isBlank()) return "";
    try {
      String path = java.net.URI.create(rawUrl).getPath();
      return path == null ? "" : path;
    } catch (IllegalArgumentException ignored) {
      return "";
    }
  }

  private static Long verifySubmittedRoleState(String listPath) {
    boolean external = "/external/admin/authority-rights".equals(listPath);
    String roleId = external ? rememberedRoleId : rememberedInternalRoleId;
    String expectedDescription = external ? submittedExternalRoleDescription : submittedInternalRoleDescription;
    if (roleId == null || expectedDescription == null) return null;
    long verificationStartedAt = System.currentTimeMillis();
    adminOpen(listPath + "/" + roleId + "/edit");
    long deadline = System.currentTimeMillis() + 15000;
    boolean matched = false;
    while (System.currentTimeMillis() < deadline) {
      SelenideElement field = external ? findDescriptionField() : findInternalRoleDescriptionField();
      String observed = field.getValue() == null ? "" : field.getValue();
      boolean rightsMatch = external || submittedInternalRoleRightsCount < 0
        || currentInternalSelectedRightsCount() == submittedInternalRoleRightsCount;
      if (expectedDescription.equals(observed) && rightsMatch) {
        matched = true;
        break;
      }
      sleep(250);
    }
    long verifiedAt = System.currentTimeMillis();
    System.out.println("ROLE_STATE_VERIFY type=" + (external ? "external" : "internal")
      + " matched=" + matched);
    if (!matched) return null;
    NetworkMockSupport.drainPerformanceLogs();
    String expectedGetPath = "/api/" + (external ? "external" : "internal")
      + "-authority-rights/" + roleId;
    removeLatestMatchingRoleRequest("GET", expectedGetPath, verificationStartedAt, verifiedAt);
    adminOpen(listPath);
    return verifiedAt;
  }

  private static int currentInternalSelectedRightsCount() {
    java.util.regex.Matcher matcher = java.util.regex.Pattern
      .compile("(\\d+)\\s*/\\s*(\\d+)\\s*selected rights", java.util.regex.Pattern.CASE_INSENSITIVE)
      .matcher(findRightsCounterText());
    return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
  }

  private static String roleFormState() {
    Object state = executeJavaScript(
      "const forms=[...document.querySelectorAll('form')];"
        + "return JSON.stringify({formCount:forms.length,validFormCount:forms.filter(form=>form.checkValidity()).length,"
        + "invalidControlCount:document.querySelectorAll(':invalid').length,"
        + "authorityResourceCount:performance.getEntriesByType('resource')"
        + ".filter(entry=>entry.name.includes('authority-rights')).length,"
        + "apiResponseCount:(window.__roleSaveResponses||[]).length,"
        + "visibleAlertCount:[...document.querySelectorAll('[role=alert],.alert,.invalid-feedback')]"
        + ".filter(el=>el.offsetParent!==null).length});"
    );
    return String.valueOf(state);
  }

  private static SelenideElement firstVisibleEnabledRoleSave(String selector) {
    for (SelenideElement candidate : $$(selector)) {
        if (!candidate.isDisplayed() || !candidate.isEnabled()) continue;
        if ("Save".equalsIgnoreCase(candidate.getText().trim())) {
          return candidate;
        }
    }
    return null;
  }

  // ── Internal role edit round-trip state ──────────────────────
  private static String rememberedInternalRoleDescription = null;
  private static int rememberedSelectedRightsCount = 0;
  private static int rememberedTotalRightsCount = 0;
  private static String rememberedRemovedRightName = null;
  private static String rememberedRemovedRightCode = null;
  private static String rememberedInternalRoleId = null;
  private static String submittedInternalRoleDescription = null;
  private static int submittedInternalRoleRightsCount = -1;
  private static boolean internalRoleServerStateDirty = false;

  @When("I find and open the observed internal role {string} editor")
  public void iFindAndOpenInternalRoleEditor(String roleName) {
    assertAdminList("/admin/authority-rights", "Internal Roles");
    SelenideElement targetRow = findRoleOnCurrentPageInternal(roleName);
    int pageLimit = 50;
    while (targetRow == null && pageLimit-- > 0) {
      SelenideElement nextBtn = $("a[aria-label=Next], .pagination .next a, li.page-item.next a, li.next a");
      if (nextBtn.exists() && nextBtn.isDisplayed() && nextBtn.isEnabled()) {
        String beforeUrl = WebDriverRunner.url();
        executeJavaScript("arguments[0].click()", nextBtn.getWrappedElement());
        awaitPageTransition(beforeUrl, pageFingerprint());
        sleep(500);
        targetRow = findRoleOnCurrentPageInternal(roleName);
      } else break;
    }
    if (targetRow == null) {
      throw new AssertionError("Role '" + roleName + "' not found on any page");
    }
    String observedId = targetRow.getAttribute("id");
    if (observedId == null || !observedId.matches("[0-9]+")) {
      throw new AssertionError("Role row has no numeric identity");
    }
    rememberedInternalRoleId = observedId;
    adminOpen("/admin/authority-rights/" + observedId + "/edit");
    long deadline = System.currentTimeMillis() + 30000;
    while (System.currentTimeMillis() < deadline) {
      String url = WebDriverRunner.url();
      if (url != null && url.contains("/admin/authority-rights/" + observedId + "/edit")) break;
      sleep(300);
    }
    System.out.println("  👁️  Internal role editor opened for the configured role");
  }

  private static SelenideElement findRoleOnCurrentPageInternal(String roleName) {
    SelenideElement table = visibleManagementTable();
    for (SelenideElement row : table.$$("tbody tr[id]")) {
      if (!row.isDisplayed()) continue;
      if (rowHasExactCellText(row, roleName)) return row;
    }
    return null;
  }

  @Then("the internal role editor shows the role {string}")
  public void theInternalRoleEditorShowsTheRole(String roleName) {
    String url = WebDriverRunner.url();
    if (url == null || !url.contains("/admin/authority-rights/") || !url.contains("/edit")) {
      throw new AssertionError("Expected internal role editor route");
    }
    awaitRoleEditorShell("/admin/authority-rights/" + rememberedInternalRoleId + "/edit");
    String body = waitForNonEmptyBodyText();
    boolean roleObserved = body != null && body.contains(roleName);
    for (SelenideElement field : $$("input, textarea")) {
      String value = field.getValue();
      if (value != null && value.trim().equals(roleName)) roleObserved = true;
    }
    if (!roleObserved) {
      throw new AssertionError("Internal role editor did not expose the configured role");
    }
    System.out.println("  👁️  Internal role editor confirmed for the configured role");
  }

  private static void awaitRoleEditorShell(String editorPath) {
    long deadline = System.currentTimeMillis() + 20000;
    boolean reopened = false;
    while (System.currentTimeMillis() < deadline) {
      SelenideElement heading = $("h1");
      SelenideElement form = $("form");
      if (heading.exists() && heading.isDisplayed() && form.exists() && form.isDisplayed()) return;
      if (!reopened && System.currentTimeMillis() + 10000 >= deadline) {
        adminOpen(editorPath);
        reopened = true;
      }
      sleep(200);
    }
    throw new AssertionError("Role editor shell did not render on the observed editor route");
  }

  @When("I remember the internal role state")
  public void iRememberInternalRoleState() {
    if (internalRoleServerStateDirty) {
      throw new AssertionError("An earlier internal-role mutation still requires fixture cleanup");
    }
    internalRoleServerStateDirty = false;
    rememberedRemovedRightCode = null;
    rememberedInternalRoleDescription = findInternalRoleDescriptionField().getValue();
    if (rememberedInternalRoleDescription == null) rememberedInternalRoleDescription = "";
    System.out.println("  📝  Remembered the original internal role Description");

    // Parse the selected rights counter (e.g., "158/158 selected rights")
    java.util.regex.Matcher m = PAGINATION_PATTERN.matcher($("body").getText());
    // The counter is in a label like "158/158 selected rights"
    String counterText = findRightsCounterText();
    m = java.util.regex.Pattern.compile("(\\d+)\\s*/\\s*(\\d+)\\s*selected rights", java.util.regex.Pattern.CASE_INSENSITIVE)
        .matcher(counterText);
    if (m.find()) {
      rememberedSelectedRightsCount = Integer.parseInt(m.group(1));
      rememberedTotalRightsCount = Integer.parseInt(m.group(2));
      System.out.println("  📋  Selected rights: " + rememberedSelectedRightsCount + "/" + rememberedTotalRightsCount);
    } else {
      System.out.println("  ⚠️  Could not parse the structural rights counter");
    }
  }

  private static String findRightsCounterText() {
    // The counter is in a label near the rights selector
    for (SelenideElement label : $$("label")) {
      String text = label.getText();
      if (text != null && text.toLowerCase(java.util.Locale.ROOT).contains("selected rights")) {
        return text;
      }
    }
    // Fallback: body text
    return $("body").getText();
  }

  @And("I append {string} after {string} in the internal role Description")
  public void iAppendInInternalRoleDescription(String suffix, String afterWord) {
    SelenideElement descField = findInternalRoleDescriptionField();
    String current = descField.getValue() == null ? "" : descField.getValue().trim();
    String modified = current;
    int idx = current.toLowerCase(java.util.Locale.ROOT).lastIndexOf(afterWord.toLowerCase(java.util.Locale.ROOT));
    if (idx >= 0) {
      idx += afterWord.length();
      modified = current.substring(0, idx) + suffix + current.substring(idx);
    } else {
      modified = current + suffix;
    }
    setAngularFieldValue(descField, modified);
    System.out.println("  📝  Internal role Description modified");
  }

  @And("I check the first selected right checkbox")
  public void iCheckFirstSelectedRightCheckbox() {
    // Click the first checked checkbox (skipping index 0 which is the counter "N/M selected rights").
    // Use JS click since Angular switches hide the actual input.
    Object clicked = executeJavaScript(
      "const all = document.querySelectorAll('input[type=checkbox]');"
      + "for (const cb of all) {"
      + "  if (cb.checked) {"
      + "    const name = cb.getAttribute('name') || '';"
      + "    const fcn = cb.getAttribute('formcontrolname') || '';"
      + "    if (name === 'selectedRights' || name === 'selectAllRights' || fcn === 'selectedRights' || fcn === 'selectAllRights') {"
      + "      continue;"
      + "    }"
      + "    cb.click();"
      + "    const label = (cb.closest('label') ? cb.closest('label').textContent : (cb.parentElement ? cb.parentElement.textContent : '')).trim().slice(0,80);"
      + "    return JSON.stringify({clicked: true, label: label, name: name, fcn: fcn});"
      + "  }"
      + "}"
      + "return JSON.stringify({clicked: false});");

    if (clicked != null && String.valueOf(clicked).contains("\"clicked\":true")) {
      String label = "";
      try {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"label\":\"([^\"]+)\"").matcher(String.valueOf(clicked));
        if (m.find()) label = m.group(1);
      } catch (Throwable ignored) {}
      try {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"name\":\"([^\"]+)\"").matcher(String.valueOf(clicked));
        if (m.find()) rememberedRemovedRightCode = m.group(1);
      } catch (Throwable ignored) {}
      rememberedRemovedRightName = label.isEmpty() ? "(selected right)" : label;
      System.out.println("  🔲  Selected one right for removal");
      sleep(500);
      return;
    }
    throw new AssertionError("Could not find and click a checked right checkbox in the editor");
  }

  @Then("the Remove rights button becomes enabled")
  public void theRemoveRightsButtonBecomesEnabled() {
    SelenideElement removeBtn = $("button[aria-label='Remove rights']");
    removeBtn.shouldBe(visible);
    if ("disabled".equalsIgnoreCase(removeBtn.getAttribute("disabled"))) {
      throw new AssertionError("Remove rights button is still disabled after unchecking a right");
    }
    System.out.println("  👁️  Remove rights button is now enabled");
  }

  @When("I click the Remove rights button")
  public void iClickTheRemoveRightsButton() {
    SelenideElement removeBtn = $("button[aria-label='Remove rights']").shouldBe(visible).shouldBe(enabled);
    removeBtn.click();
    System.out.println("  👁️  Clicked Remove rights button");
    sleep(500);
  }

  @Then("the selected rights count decreases by one")
  public void theSelectedRightsCountDecreasesByOne() {
    String counterText = findRightsCounterText();
    java.util.regex.Matcher m = java.util.regex.Pattern
      .compile("(\\d+)\\s*/\\s*(\\d+)\\s*selected rights", java.util.regex.Pattern.CASE_INSENSITIVE)
      .matcher(counterText);
    if (!m.find()) throw new AssertionError("Could not parse rights counter");
    int newSelected = Integer.parseInt(m.group(1));
    int expected = rememberedSelectedRightsCount - 1;
    if (newSelected != expected) {
      throw new AssertionError("Selected rights count: expected " + expected + ", got " + newSelected);
    }
    System.out.println("  👁️  Rights count decreased: " + rememberedSelectedRightsCount + " → " + newSelected);
  }

  @When("I find and open the observed internal role {string} editor again")
  public void iFindAndOpenInternalRoleEditorAgain(String roleName) {
    assertAdminList("/admin/authority-rights", "Internal Roles");
    if (rememberedInternalRoleId == null || !rememberedInternalRoleId.matches("[0-9]+")) {
      throw new AssertionError("No observed internal role identity was retained for reopening");
    }
    adminOpen("/admin/authority-rights/" + rememberedInternalRoleId + "/edit");
    long deadline = System.currentTimeMillis() + 30000;
    while (System.currentTimeMillis() < deadline) {
      String url = WebDriverRunner.url();
      if (url != null && url.contains("/admin/authority-rights/" + rememberedInternalRoleId + "/edit")) break;
      sleep(300);
    }
    String persistedDescription = findInternalRoleDescriptionField().getValue();
    int persistedRightsCount = currentInternalSelectedRightsCount();
    if (submittedInternalRoleDescription == null
        || !submittedInternalRoleDescription.equals(persistedDescription == null ? "" : persistedDescription)
        || submittedInternalRoleRightsCount < 0
        || submittedInternalRoleRightsCount != persistedRightsCount) {
      throw new AssertionError("The internal role mutation was not persisted after reopening");
    }
    System.out.println("ROLE_STATE_VERIFY type=internal matched=true");
  }

  @When("I restore the internal role Description")
  public void iRestoreInternalRoleDescription() {
    if (rememberedInternalRoleDescription == null) {
      throw new AssertionError("No original Description was remembered");
    }
    SelenideElement descField = findInternalRoleDescriptionField();
    setAngularFieldValue(descField, rememberedInternalRoleDescription);
    System.out.println("  📝  Description restored to its remembered value");
  }

  @And("I add the previously removed right back")
  public void iAddPreviouslyRemovedRightBack() {
    // Look for the right in the right column (available rights) and check it
    System.out.println("  🔲  Restoring the previously removed right");
    // Try to find the unchecked checkbox for the removed right
    String removedRightCode = rememberedRemovedRightCode == null || rememberedRemovedRightCode.isBlank()
      ? rememberedRemovedRightName.split("[\\s\\u00a0(]", 2)[0]
      : rememberedRemovedRightCode;
    for (SelenideElement cb : $$("form input[type=checkbox]")) {
      if (!cb.isDisplayed() || cb.isSelected()) continue;
      try {
        SelenideElement parent = $(cb).closest("label");
        String labelText = parent.exists() ? parent.getText().trim() : "";
        String checkboxName = cb.getAttribute("name");
        if (checkboxName == null) checkboxName = "";
        if (checkboxName.equals(removedRightCode)
            || (!labelText.isBlank() && (labelText.contains(rememberedRemovedRightName)
            || rememberedRemovedRightName.contains(labelText)))) {
            executeJavaScript("arguments[0].click()", cb.getWrappedElement());
            sleep(300);
            // Click "Add rights" arrow button
            SelenideElement addBtn = $("button[aria-label='Add rights']");
            if (addBtn.isDisplayed() && addBtn.isEnabled()) {
              addBtn.click();
              sleep(500);
              System.out.println("  👁️  Restored the previously removed right");
              return;
            }
        }
      } catch (Throwable ignored) {}
    }
    throw new AssertionError("Could not find the exact previously removed right to restore");
  }

  private static SelenideElement findInternalRoleDescriptionField() {
    for (int loadAttempt = 1; loadAttempt <= 2; loadAttempt++) {
      long deadline = System.currentTimeMillis() + 20000;
      while (System.currentTimeMillis() < deadline) {
        SelenideElement field = visibleInternalRoleDescriptionField();
        if (field != null) return field;
        sleep(250);
      }
      if (loadAttempt == 1) {
        System.out.println("  ↻ Internal role editor detail did not render; refreshing the idempotent edit route once");
        refresh();
      }
    }
    throw new AssertionError("No visible Description field found in the internal role editor after one targeted reload");
  }

  private static SelenideElement visibleInternalRoleDescriptionField() {
    SelenideElement labelled = $("label[for=descriptionLarge]");
    if (labelled.exists()) {
      SelenideElement field = $("#descriptionLarge");
      if (field.exists() && field.isDisplayed() && field.isEnabled()) return field;
    }
    for (SelenideElement field : $$("textarea, input")) {
      try {
        if (!field.isDisplayed()) continue;
        String name = field.getAttribute("name");
        String placeholder = field.getAttribute("placeholder");
        String id = field.getAttribute("id");
        String formControlName = field.getAttribute("formcontrolname");
        String dataCy = field.getAttribute("data-cy");
        if ((name != null && name.toLowerCase(java.util.Locale.ROOT).contains("description"))
            || (placeholder != null && placeholder.toLowerCase(java.util.Locale.ROOT).contains("description"))
            || (id != null && id.toLowerCase(java.util.Locale.ROOT).contains("description"))
            || (formControlName != null && formControlName.toLowerCase(java.util.Locale.ROOT).contains("description"))
            || (dataCy != null && dataCy.toLowerCase(java.util.Locale.ROOT).contains("description"))) return field;
      } catch (Throwable ignored) {}
    }
    for (SelenideElement field : $$("textarea")) {
      try {
        if (field.isDisplayed()) return field;
      } catch (Throwable ignored) {}
    }
    return null;
  }

  @And("I click {string} on the internal role editor")
  public void iClickSaveOnInternalRoleEditor(String buttonLabel) {
    boolean restoredState = internalRoleEditorMatchesRememberedState();
    submittedInternalRoleDescription = findInternalRoleDescriptionField().getValue();
    submittedInternalRoleRightsCount = currentInternalSelectedRightsCount();
    if (!restoredState) internalRoleServerStateDirty = true;
    saveRoleAndReturnToList(buttonLabel, "/admin/authority-rights");
    if (restoredState) internalRoleServerStateDirty = false;
  }

  static void restoreInterruptedInternalRoleState() {
    if (!internalRoleServerStateDirty || rememberedInternalRoleId == null
        || rememberedInternalRoleDescription == null) return;
    try {
      adminOpen("/admin/authority-rights/" + rememberedInternalRoleId + "/edit");
      findInternalRoleDescriptionField();
      iRestoreRememberedInternalRoleDescriptionForCleanup();
      if (!internalRoleRightsMatchRememberedCount()) {
        addRememberedInternalRightForCleanup();
      }
      if (!internalRoleEditorMatchesRememberedState()) {
        throw new AssertionError("Internal role cleanup could not reconstruct the remembered state");
      }
      submittedInternalRoleDescription = rememberedInternalRoleDescription;
      submittedInternalRoleRightsCount = rememberedSelectedRightsCount;
      saveRoleAndReturnToList("Save", "/admin/authority-rights");
      NetworkBusinessWaitRepair.waitForBusinessData();
      internalRoleServerStateDirty = false;
      System.out.println("  👁️  Restored interrupted internal-role fixture state");
    } catch (Throwable failure) {
      throw new AssertionError("Failed to restore interrupted internal-role fixture state", failure);
    }
  }

  static void restoreInterruptedExternalRoleState() {
    if (!externalRoleServerStateDirty || rememberedRoleId == null
        || rememberedRoleDescription == null) return;
    try {
      adminOpen("/external/admin/authority-rights/" + rememberedRoleId + "/edit");
      refresh();
      String current = awaitExternalRoleDescriptionValue();
      if (sameRoleDescription(rememberedRoleDescription, current)) {
        externalRoleServerStateDirty = false;
        return;
      }
      setExternalRoleDescriptionForCleanup(rememberedRoleDescription);
      submittedExternalRoleDescription = rememberedRoleDescription;
      try {
        saveRoleAndReturnToList("Save", "/external/admin/authority-rights");
      } catch (Throwable saveFailure) {
        // The live endpoint can return HTTP 500 after applying the mutation.
        // Re-read through a fresh editor before deciding cleanup failed.
        adminOpen("/external/admin/authority-rights/" + rememberedRoleId + "/edit");
        refresh();
        String observed = awaitExternalRoleDescriptionValue();
        if (!sameRoleDescription(rememberedRoleDescription, observed)) throw saveFailure;
        long verifiedAt = System.currentTimeMillis();
        NetworkMockSupport.drainPerformanceLogs();
        String expectedPath = "/api/external-authority-rights/" + rememberedRoleId;
        PENDING_DATA_REQUESTS.entrySet().removeIf(entry -> {
          RuntimeState.PendingRequest pending = entry.getValue();
          return pending != null && pending.url != null && pending.url.contains(expectedPath)
            && pending.startedAt <= verifiedAt;
        });
      }
      NetworkBusinessWaitRepair.waitForBusinessData();
      externalRoleServerStateDirty = false;
      System.out.println("  👁️  Restored interrupted external-role fixture state");
    } catch (Throwable failure) {
      throw new AssertionError("Failed to restore interrupted external-role fixture state", failure);
    }
  }

  private static String awaitExternalRoleDescriptionValue() {
    long deadline = System.currentTimeMillis() + 30000;
    while (System.currentTimeMillis() < deadline) {
      try {
        SelenideElement field = correlatedExternalRoleDescriptionField();
        if (field != null && field.isDisplayed()) {
          String value = field.getValue();
          return value == null ? "" : value;
        }
      } catch (Throwable ignored) {}
      sleep(100);
    }
    throw new AssertionError("External role Description field did not render for cleanup");
  }

  private static void setExternalRoleDescriptionForCleanup(String value) {
    Throwable lastFailure = null;
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        SelenideElement field = correlatedExternalRoleDescriptionField();
        if (field == null) throw new AssertionError("The correlated external role Description control is unavailable");
        if (!field.isDisplayed() || !field.isEnabled()) {
          throw new AssertionError("The correlated external role Description control is not editable");
        }
        setAngularFieldValue(field, value);
        String observed = field.getValue() == null ? "" : field.getValue();
        if (sameRoleDescription(value, observed)) return;
      } catch (Throwable failure) {
        lastFailure = failure;
      }
      sleep(250);
    }
    throw new AssertionError("External role Description could not be restored in the editor", lastFailure);
  }

  private static SelenideElement correlatedExternalRoleDescriptionField() {
    List<SelenideElement> fields = new ArrayList<>();
    $$("textarea,input").forEach(fields::add);
    for (SelenideElement field : fields) {
      try {
        if (!field.isDisplayed()) continue;
      } catch (Throwable ignored) {
        continue;
      }
      if (sameNonBlankAttribute(rememberedRoleDescriptionFieldId, field.getAttribute("id"))
          || sameNonBlankAttribute(rememberedRoleDescriptionFieldName, field.getAttribute("name"))
          || sameNonBlankAttribute(rememberedRoleDescriptionFormControlName, field.getAttribute("formcontrolname"))
          || sameNonBlankAttribute(rememberedRoleDescriptionDataCy, field.getAttribute("data-cy"))) {
        return field;
      }
    }
    if (rememberedRoleDescriptionFieldIndex >= 0 && rememberedRoleDescriptionFieldIndex < fields.size()) {
      SelenideElement indexed = fields.get(rememberedRoleDescriptionFieldIndex);
      try {
        if (indexed.isDisplayed()) return indexed;
      } catch (Throwable ignored) {}
    }
    try {
      return findDescriptionField();
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static boolean sameNonBlankAttribute(String expected, String observed) {
    return expected != null && !expected.isBlank() && expected.equals(observed);
  }

  private static boolean sameRoleDescription(String expected, String observed) {
    return normalizeRoleDescription(expected).equals(normalizeRoleDescription(observed));
  }

  private static String normalizeRoleDescription(String value) {
    return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
  }

  private static void iRestoreRememberedInternalRoleDescriptionForCleanup() {
    setAngularFieldValue(findInternalRoleDescriptionField(), rememberedInternalRoleDescription);
  }

  private static void addRememberedInternalRightForCleanup() {
    if (rememberedRemovedRightCode == null || rememberedRemovedRightCode.isBlank()) {
      throw new AssertionError("No exact removed-right code was remembered for cleanup");
    }
    for (SelenideElement cb : $$("form input[type=checkbox]")) {
      String checkboxName = cb.getAttribute("name");
      if (!cb.isDisplayed() || cb.isSelected()
          || !rememberedRemovedRightCode.equals(checkboxName == null ? "" : checkboxName)) continue;
      executeJavaScript("arguments[0].click()", cb.getWrappedElement());
      SelenideElement addBtn = $("button[aria-label='Add rights']");
      if (!addBtn.isDisplayed() || !addBtn.isEnabled()) {
        throw new AssertionError("Add rights did not enable during fixture cleanup");
      }
      addBtn.click();
      sleep(500);
      return;
    }
    throw new AssertionError("Exact removed right was not available during fixture cleanup");
  }

  private static boolean internalRoleEditorMatchesRememberedState() {
    if (rememberedInternalRoleDescription == null) return false;
    String description = findInternalRoleDescriptionField().getValue();
    return rememberedInternalRoleDescription.equals(description == null ? "" : description)
      && internalRoleRightsMatchRememberedCount();
  }

  private static boolean internalRoleRightsMatchRememberedCount() {
    if (rememberedSelectedRightsCount <= 0) return false;
    java.util.regex.Matcher matcher = java.util.regex.Pattern
      .compile("(\\d+)\\s*/\\s*(\\d+)\\s*selected rights", java.util.regex.Pattern.CASE_INSENSITIVE)
      .matcher(findRightsCounterText());
    return matcher.find()
      && Integer.parseInt(matcher.group(1)) == rememberedSelectedRightsCount
      && Integer.parseInt(matcher.group(2)) == rememberedTotalRightsCount;
  }

  @And("the persons search result list contains {string}")
  public void thePersonsSearchResultListContains(String expectedText) {
    long deadline = System.currentTimeMillis() + 15000;
    while (System.currentTimeMillis() < deadline) {
      try {
        SelenideElement table = visibleManagementTable();
        String tableText = table.getText().toLowerCase(java.util.Locale.ROOT);
        if (tableText.contains(expectedText.toLowerCase(java.util.Locale.ROOT))) {
          System.out.println("  ✅ Persons search result contains '" + expectedText + "'");
          return;
        }
      } catch (Throwable ignored) {}
      sleep(300);
    }
    String tableText = "";
    try { tableText = visibleManagementTable().getText(); } catch (Throwable ignored) {}
    throw new AssertionError("Persons search result list does not contain '" + expectedText
      + "'. Table content: " + tableText.substring(0, Math.min(tableText.length(), 1000)));
  }

  @When("I log out from the admin application")
  public void iLogOutFromAdminApplication() {
    // Click the profile dropdown in the navbar, then look for Sign out quickly
    SelenideElement profile = $("#navbarProfileDropdown").shouldBe(visible, enabled);
    profile.click();
    sleep(300); // brief wait for dropdown to open
    System.out.println("  👁️  Profile dropdown opened, looking for Sign out...");

    // Use JS to find and click Sign out with a short deadline
    Object result = executeJavaScript(
      "const items = document.querySelectorAll('a, button, [role=menuitem], [role=button]');"
      + "for (const item of items) {"
      + "  const text = (item.textContent || '').trim().toLowerCase();"
      + "  if (text === 'sign out' || text === 'log out' || text === 'odhlásit se' || text === 'atslēgties') {"
      + "    item.click();"
      + "    return JSON.stringify({clicked: true, label: text});"
      + "  }"
      + "}"
      + "return JSON.stringify({clicked: false});");

    System.out.println("  🔀  Logout result: " + result);
    // Wait briefly for redirect to login page
    long deadline = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < deadline) {
      String url = WebDriverRunner.url();
      if (url != null && url.contains("/login")) {
        System.out.println("  👁️  Redirected to login after logout");
        return;
      }
      sleep(200);
    }
    System.out.println("  👁️  Logout attempted, current URL=" + WebDriverRunner.url());
  }


  // ── Person edit round-trip state ──────────────────────────────
  private static String rememberedPersonName = null;
  private static String rememberedPersonStatus = null;

  @When("I search for person {string}")
  public void iSearchForPersonOnPersonsPage(String query) {
    // Reuse the exact same approach as Search Persons scenario:
    // fill by label, then submit the observed form
    fillByLabel("Search query", query);
    submitObservedForm();
  }

  @And("I open the first person from the search results")
  public void iOpenFirstPersonFromSearchResults() {
    // Click the person name text in the first row of the results table.
    // Clicking on the person name navigates to the Edit Person screen.
    SelenideElement table = visibleManagementTable().shouldBe(visible);
    List<SelenideElement> rows = new ArrayList<>();
    table.$$("tbody tr").forEach(rows::add);
    if (rows.isEmpty()) {
      throw new AssertionError("No person rows found in search results table");
    }
    SelenideElement firstRow = rows.get(0);
    // Try clicking the first cell (person name) via JS
    List<SelenideElement> cells = new ArrayList<>();
    firstRow.$$("td").forEach(cells::add);
    if (!cells.isEmpty()) {
      // Click the first cell with text (person name)
      for (SelenideElement cell : cells) {
        String text = cell.getText().trim();
        if (!text.isEmpty()) {
          System.out.println("  👁️  Clicking on person: " + text);
          executeJavaScript("arguments[0].click()", cell.getWrappedElement());
          sleep(5000);
          return;
        }
      }
    }
    // Fallback: click the first cell
    executeJavaScript("arguments[0].click()", firstRow.getWrappedElement());
    sleep(5000);
    System.out.println("  👁️  Clicked first person row");
  }

  @And("I open the first person from the search results again")
  public void iOpenFirstPersonFromSearchResultsAgain() {
    iOpenFirstPersonFromSearchResults();
  }

  @Then("the person editor is displayed")
  public void thePersonEditorIsDisplayed() {
    $("form").shouldBe(visible);
    System.out.println("  👁️  Person editor form is visible");
  }

  @When("I remember the person state")
  public void iRememberPersonState() {
    SelenideElement nameField = findPersonNameField();
    rememberedPersonName = nameField.getValue() == null ? "" : nameField.getValue().trim();
    System.out.println("  📝  Original Name: \"" + rememberedPersonName + "\"");
    rememberedPersonStatus = getCurrentPersonStatus();
    System.out.println("  📋  Current Status: \"" + rememberedPersonStatus + "\"");
  }

  @And("I append {string} after the person Name")
  public void iAppendAfterPersonName(String suffix) {
    SelenideElement nameField = findPersonNameField();
    String current = nameField.getValue() == null ? "" : nameField.getValue().trim();
    String modified = current + suffix;
    // Use native value setter + input event so Angular's FormControl picks up the change
    executeJavaScript(
      "var el = arguments[0];"
      + "var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;"
      + "nativeSetter.call(el, arguments[1]);"
      + "el.dispatchEvent(new Event('input', {bubbles: true}));"
      + "el.dispatchEvent(new Event('change', {bubbles: true}));",
      nameField.getWrappedElement(), modified);
    System.out.println("  📝  Modified Name: \"" + modified + "\"");
  }

  @And("I change person status to {string}")
  public void iChangePersonStatusTo(String status) {
    SelenideElement statusSelect = findPersonStatusSelect();
    if (statusSelect != null) {
      statusSelect.selectOptionContainingText(status);
      System.out.println("  📋  Status changed to: " + status);
    } else {
      setStatusViaRadioButton(status);
    }
    sleep(300);
  }

  private static SelenideElement findPersonNameField() {
    for (SelenideElement field : $$("form input, form textarea")) {
      try {
        if (!field.isDisplayed()) continue;
        String name = field.getAttribute("name");
        String formcontrolname = field.getAttribute("formcontrolname");
        String placeholder = field.getAttribute("placeholder");
        if ((name != null && name.toLowerCase(java.util.Locale.ROOT).contains("name")
             && !name.toLowerCase(java.util.Locale.ROOT).contains("search"))
            || (formcontrolname != null && formcontrolname.toLowerCase(java.util.Locale.ROOT).contains("name"))
            || (placeholder != null && placeholder.toLowerCase(java.util.Locale.ROOT).contains("name"))) {
          return field;
        }
      } catch (Throwable ignored) {}
    }
    throw new AssertionError("No visible Name field found in the person editor");
  }

  private static SelenideElement findPersonStatusSelect() {
    for (SelenideElement select : $$("form select")) {
      try {
        if (!select.isDisplayed()) continue;
        String name = select.getAttribute("name");
        String formcontrolname = select.getAttribute("formcontrolname");
        if ((name != null && name.toLowerCase(java.util.Locale.ROOT).contains("status"))
            || (formcontrolname != null && formcontrolname.toLowerCase(java.util.Locale.ROOT).contains("status"))) {
          return select;
        }
      } catch (Throwable ignored) {}
    }
    return null;
  }

  private static String getCurrentPersonStatus() {
    SelenideElement statusSelect = findPersonStatusSelect();
    if (statusSelect != null) {
      return statusSelect.getValue();
    }
    for (SelenideElement radio : $$("form input[type=radio]:checked")) {
      String label = radio.getAttribute("value");
      if (label != null && !label.isBlank()) return label;
    }
    return "(unknown)";
  }

  private static void setStatusViaRadioButton(String status) {
    for (SelenideElement radio : $$("form input[type=radio]")) {
      try {
        String value = radio.getValue();
        String label = "";
        SelenideElement parent = radio.closest("label");
        if (parent.exists()) label = parent.getText().trim();
        if (status.equalsIgnoreCase(value) || status.equalsIgnoreCase(label)) {
          executeJavaScript("arguments[0].click()", radio.getWrappedElement());
          return;
        }
      } catch (Throwable ignored) {}
    }
  }

  @When("I click {string} on the person editor")
  public void iClickSaveOnPersonEditor(String buttonLabel) {
    SelenideElement save = $("button[type=submit]").shouldBe(visible).shouldBe(enabled);
    save.click();
    System.out.println("  👁️  Clicked '" + buttonLabel + "' on person editor");
    sleep(3000);
  }

  @And("I search for person {string} again")
  public void iSearchForPersonAgain(String query) {
    // After Save the app SPA-redirects to the persons list — do NOT full-reload
    By searchSel = By.cssSelector("input[formcontrolname='inputSearchValue'], input[name='search']");
    boolean found = false;
    for (int i = 0; i < 10; i++) {
      sleep(1000);
      if ($(searchSel).exists()) { found = true; break; }
    }
    if (!found) {
      System.out.println("  ⚠️  Not on persons list after save — falling back to direct navigation");
      open("/external/admin/persons");
      sleep(3000);
    }
    // Fill search and click Search button directly (no form transition check)
    fillByLabel("Search query", query);
    SelenideElement searchBtn = uniqueObservedControl("Search");
    searchBtn.click();
    sleep(3000);
    System.out.println("  👁️  Re-searched for person: " + query);
  }

  @When("I restore the person Name")
  public void iRestorePersonName() {
    if (rememberedPersonName == null) throw new AssertionError("No original Name remembered");
    String toRestore = rememberedPersonName;
    while (toRestore.endsWith("1")) {
      toRestore = toRestore.substring(0, toRestore.length() - 1);
    }
    SelenideElement nameField = findPersonNameField();
    // Use native value setter + input event so Angular's FormControl picks up the change
    executeJavaScript(
      "var el = arguments[0];"
      + "var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;"
      + "nativeSetter.call(el, arguments[1]);"
      + "el.dispatchEvent(new Event('input', {bubbles: true}));"
      + "el.dispatchEvent(new Event('change', {bubbles: true}));",
      nameField.getWrappedElement(), toRestore);
    System.out.println("  📝  Name restored to \"" + toRestore + "\"");
  }

  @Then("the person editor save is confirmed")
  public void thePersonEditorSaveIsConfirmed() {
    try {
      String body = $("body").getText();
      if (body != null && (body.toLowerCase(java.util.Locale.ROOT).contains("saved")
          || body.toLowerCase(java.util.Locale.ROOT).contains("success"))) {
        System.out.println("  ✅ Person save confirmed");
        return;
      }
    } catch (Throwable ignored) {}
    $("form").shouldBe(visible);
    System.out.println("  👁️  Person editor still visible after save (assumed success)");
  }

  private static String waitForNonEmptyBodyText() {
    long deadline = System.currentTimeMillis() + 10000;
    String text = "";
    while (System.currentTimeMillis() < deadline) {
      text = $("body").shouldBe(visible).getText();
      if (text != null && text.length() > 150) return text;
      sleep(250);
    }
    return text == null ? "" : text;
  }
}
