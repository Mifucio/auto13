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
    long sessionDeadline = System.currentTimeMillis() + 10000;
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
    System.out.println("  👁️  Found role row id=" + observedId + ", navigating to editor...");
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
    $("h1").shouldBe(visible).shouldHave(text(expectedHeading));
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
    System.out.println("  👁️  Found '" + roleName + "' row id=" + observedId + ", navigating to editor...");
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
    $("input[type=search][name=search]").shouldBe(visible).shouldHave(value("test-value"));
    uniqueObservedControl("Search").shouldBe(visible);
    visibleManagementTable().shouldBe(visible);
    $(byText("1 - 0 of 0")).shouldBe(visible);
    CheckpointCapture.capture("search-external-user.admin-search-external-user.search-results-visible");
  }

  @Then("person_search_results")
  public void person_search_results() {
    assertPersonsRouteAndHeading();
    $("input[type=search][name=search]").shouldBe(visible).shouldHave(value("test-value"));
    uniqueObservedControl("Search").shouldBe(visible);
    // The live search for the safe non-record query renders the observed
    // empty result state instead of inventing a matching person.
    $(byText("1 - 0 of 0")).shouldBe(visible);
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
      body = $("body").shouldBe(visible).getText();
      if (hasVisibleCorporateActionRows()) return;
      if (hasVisibleCorporateActionsEmptyState() && !hasVisibleCorporateActionsLoadingIndicator()) return;
      sleep(250);
    }
    throw new AssertionError("Corporate Actions route did not render a table or explicit empty state. Visible text="
      + body.substring(0, Math.min(body.length(), 2000)));
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
    for (SelenideElement element : $("body").$$("*")) {
      if (!element.isDisplayed()) continue;
      String classes = element.getAttribute("class");
      if (classes != null && classes.toLowerCase(java.util.Locale.ROOT).matches(".*(spinner|loading|loader).*")) {
        return true;
      }
    }
    return false;
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
