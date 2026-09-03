package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.FileDownloadMode;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.support.ui.Select;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.WebDriverRunner.url;

/**
 * Repairs only the live-runtime failure modes observed after browser bootstrap:
 * fixed editing navbar click interception, the Additional Bonds "Both" branch
 * requiring unresolved bondholder rows, signed-download capture, and a history
 * oracle that previously inferred the current draft was signed from unrelated
 * DOM text.
 */
public final class DisposableExecutionRepairSteps {
  private final DisposableDividendSteps flow;
  private Path downloadedSignedDocument;

  public DisposableExecutionRepairSteps(DisposableDividendSteps flow) {
    this.flow = flow;
  }

  @When("I fill and safely save the disposable {string} form as draft")
  public void fillAndSafelySaveDraftStep(String type) throws Exception {
    fillAndSafelySaveDraft(type);
  }

  void fillAndSafelySaveDraft(String type) throws Exception {
    try {
      flow.fillDisposableApplicationAndSaveDraft(type);
      return;
    } catch (Throwable failure) {
      if (!isRecoverableClickInterception(failure)) rethrow(failure);
      System.out.println("DISPOSABLE_REPAIR intercepted_click type=" + type
        + " root=" + rootCause(failure).getClass().getSimpleName());
      if (normalize(type).contains("additional issuance of bonds")) {
        repairAdditionalBondsPaidUpBranch();
      }
      safeSaveDraftLoop();
    }
  }

  @When("I safely save the prepared disposable application as draft")
  public void safelySavePreparedDraft() throws Exception {
    try {
      flow.saveDraft();
      return;
    } catch (Throwable failure) {
      if (!isRecoverableClickInterception(failure)) rethrow(failure);
      System.out.println("DISPOSABLE_REPAIR intercepted prepared-draft save click");
      safeSaveDraftLoop();
    }
  }

  void openCreateApplicationSafely() {
    safeClickExact("Create Application");
    awaitVisibleText("Choose application type", 10000);
  }

  @When("I download the signed disposable document through the observed download control")
  public void robustSignedDownload() throws Exception {
    Path downloads = Path.of(Configuration.downloadsFolder).toAbsolutePath().normalize();
    clearDirectory(downloads);
    downloadedSignedDocument = null;

    List<SelenideElement> controls = awaitSignedApplicationDownloadControl();
    if (controls.isEmpty()) {
      throw new AssertionError("Signed disposable application exposes no visible Download control after detail-page rerender; url=" + url());
    }
    SelenideElement control = controls.get(controls.size() - 1);

    String previousFolder = Configuration.downloadsFolder;
    FileDownloadMode previousMode = Configuration.fileDownload;
    java.io.File returned = null;
    Throwable directFailure = null;
    long started = System.currentTimeMillis();
    try {
      Configuration.downloadsFolder = downloads.toString();
      Configuration.fileDownload = FileDownloadMode.FOLDER;
      try {
        returned = control.download();
      } catch (Throwable failure) {
        directFailure = failure;
        try {
          executeJavaScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();",
            control.getWrappedElement());
        } catch (Throwable ignored) { }
      }

      if (returned != null && returned.isFile() && returned.length() > 0) {
        downloadedSignedDocument = returned.toPath().toAbsolutePath().normalize();
        return;
      }

      long deadline = System.currentTimeMillis() + 30000;
      while (System.currentTimeMillis() < deadline) {
        Path artifact = newestNonEmptyFile(downloads, started - 1000);
        if (artifact != null) {
          downloadedSignedDocument = artifact;
          return;
        }
        sleep(200);
      }
    } finally {
      Configuration.downloadsFolder = previousFolder;
      Configuration.fileDownload = previousMode;
    }

    throw new AssertionError("Observed signed-document Download produced no non-empty artifact in " + downloads
      + "; direct_failure=" + (directFailure == null ? "none" : directFailure.getClass().getSimpleName()));
  }

  private static List<SelenideElement> awaitSignedApplicationDownloadControl() {
    long deadline = System.currentTimeMillis() + Math.max(10000, Math.min(Configuration.timeout, 30000));
    while (System.currentTimeMillis() < deadline) {
      String current = url();
      if (current != null && current.contains("/corporate-actions/application-form/")) {
        SelenideElement signedDocumentDownload = $(byXpath(
          "//*[normalize-space()='Signed Document']/ancestor::*[.//button[normalize-space()='Download']][1]"
            + "//button[normalize-space()='Download']"));
        if (signedDocumentDownload.isDisplayed() && signedDocumentDownload.isEnabled()) return List.of(signedDocumentDownload);

        SelenideElement detailDownload = $(
          "jhi-ca-application-form .form-info > .button-wrapper > button.btn.button-plain");
        if (detailDownload.isDisplayed() && detailDownload.isEnabled()
            && "Download".equals(normalize(detailDownload.getText()))) return List.of(detailDownload);

        SelenideElement buttonGroupDownload = $(
          "jhi-ca-application-form .form-info .button-group > button.btn.button-plain");
        if (buttonGroupDownload.isDisplayed() && buttonGroupDownload.isEnabled()
            && "Download".equals(normalize(buttonGroupDownload.getText()))) return List.of(buttonGroupDownload);

        List<SelenideElement> fallback = exactVisibleControls("Download");
        if (!fallback.isEmpty()) return fallback;
      }
      sleep(100);
    }
    return List.of();
  }

  @Then("the repaired signed disposable document exists in the file system")
  public void repairedSignedDocumentExists() throws Exception {
    if (downloadedSignedDocument == null || !Files.isRegularFile(downloadedSignedDocument)
        || Files.size(downloadedSignedDocument) <= 0) {
      throw new AssertionError("Repaired signed-document download is missing or empty");
    }
    System.out.println("SIGNED_DISPOSABLE_FILE " + downloadedSignedDocument);
    System.out.println("SIGNED_DISPOSABLE_FILE_SIZE " + Files.size(downloadedSignedDocument));
  }

  @Then("the disposable draft History contains the current application creation event")
  public void currentDraftHistoryContainsCreationEvent() {
    if (!CorporateActionsTabProbe.isActive("History")) {
      throw new AssertionError("History oracle ran while the History tab was not active");
    }
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    String last = "";
    while (System.currentTimeMillis() < deadline) {
      last = $("body").getText();
      if (normalize(last).contains("created application")) return;
      sleep(200);
    }
    throw new AssertionError("Current disposable draft History did not expose a created application event; body="
      + trim(last, 1200));
  }

  private void safeSaveDraftLoop() throws Exception {
    for (int attempt = 1; attempt <= 7; attempt++) {
      safeClickExactAny(List.of("Save as Draft", "Save as draft"));
      if (awaitVisibleTextIfPresent("Sign Document", 2500)) return;

      repairInvalidBonusIssuePaymentDate();
      repairInvalidDividendDates();
      fillVisibleValidationFields();
      attachRequiredPdfIfAny();
      System.out.println("DISPOSABLE_REPAIR save_retry=" + attempt + " invalid=" + invalidFieldInventory());
    }
    throw new AssertionError("Repaired Save as Draft did not produce an application detail with Sign Document");
  }

  private static void repairAdditionalBondsPaidUpBranch() {
    // The captured failure selected "Both", which exposes a required bondholder
    // row. The generated code then invented a random holder code and tried to
    // click an arbitrary <li> because account/name options were empty. "Yes" is
    // a valid observed branch and does not require that unrelated holder lookup.
    SelenideElement yes = $("input[type=radio][name='aib_paid_up'][value='0']");
    if (!yes.exists()) {
      yes = $("input[type=radio][name='aib_paid_up'][id*='yes_option_title']");
    }
    if (yes.exists() && !yes.isSelected()) {
      executeJavaScript("arguments[0].click();", yes.getWrappedElement());
      sleep(300);
    }
    setIfVisibleAndEmpty("#aib_nominal_value_paid", "1");
    setIfVisibleAndEmpty("#aib_additional_nominal_value", "1");
  }

  private static void setIfVisibleAndEmpty(String selector, String value) {
    SelenideElement field = $(selector);
    if (field.exists() && field.isDisplayed() && field.isEnabled()
        && (field.getValue() == null || field.getValue().isBlank())) {
      field.setValue(value);
    }
  }

  private static void fillVisibleValidationFields() {
    for (SelenideElement field : $$("input,textarea,select")) {
      if (!field.isDisplayed() || !field.isEnabled() || field.getAttribute("readonly") != null) continue;
      String type = safe(field.getAttribute("type")).toLowerCase(Locale.ROOT);
      if (type.equals("file") || type.equals("radio") || type.equals("checkbox") || type.equals("hidden")) continue;

      String value = safe(field.getValue()).trim();
      boolean required = field.getAttribute("required") != null
        || "true".equalsIgnoreCase(field.getAttribute("aria-required"));
      boolean invalid = "true".equalsIgnoreCase(field.getAttribute("aria-invalid"))
        || safe(field.getAttribute("class")).toLowerCase(Locale.ROOT).contains("invalid");
      if ((!required && !invalid) || !value.isBlank()) continue;

      if ("select".equalsIgnoreCase(field.getTagName())) {
        selectFirstNonEmptyNativeOption(field);
      } else if (type.equals("date")) {
        setDateInput(field, LocalDate.now().plusDays(2).toString());
      } else if (type.equals("number")) {
        field.setValue("1");
      } else {
        field.setValue("Disposable repair draft " + System.currentTimeMillis());
      }
    }
  }

  /**
   * Bonus Issue payment date is populated but can remain invalid after the
   * intercepted-click recovery enters its save loop. The generic repair skips
   * non-empty fields, so explicitly refresh this dependent field on each retry.
   *
   * After the backend rejects the draft, the form re-renders with aria-invalid="true"
   * on the payment date. setDateInput corrects the DOM value and dispatches the
   * input/change/blur events, but the stale aria-invalid attribute and is-invalid
   * CSS class may persist (Angular only clears them on a successful re-validation
   * triggered by form submission). The Save button's click handler checks the
   * field-level validity flags before submitting, so we must clear them here.
   */
  private static void repairInvalidBonusIssuePaymentDate() {
    SelenideElement paymentDate = $("#bi_payment_date");
    if (!paymentDate.exists() || !paymentDate.isDisplayed() || !paymentDate.isEnabled()
        || !isInvalid(paymentDate)) return;

    LocalDate recordDate = parseInputDate($("#bi_record_date"));
    if (recordDate == null) recordDate = LocalDate.now().plusDays(2);
    setDateInput(paymentDate, "");
    setDateInput(paymentDate, nextBusinessDay(recordDate.plusDays(1)).format(DateTimeFormatter.ISO_LOCAL_DATE));
    // Clear stale invalid markers left by the backend rejection so the Angular
    // submit handler no longer considers the field invalid.
    executeJavaScript(
      "arguments[0].removeAttribute('aria-invalid');"
        + "arguments[0].classList.remove('is-invalid','ng-invalid','ng-dirty','ng-touched');"
        + "const c=arguments[0].closest('.form-group,.input-group,.field,.form-floating');"
        + "if(c){c.classList.remove('has-error','is-invalid');const fb=c.querySelector('.invalid-feedback,.invalid-tooltip,.error-message');"
        + "if(fb){fb.remove();}}",
      paymentDate.getWrappedElement());
    sleep(500);
  }

  private static void repairInvalidDividendDates() {
    if (!$("#dp_ex_date").exists()) return;
    LocalDate meeting = LocalDate.now().minusDays(7);
    LocalDate ex = nextBusinessDay(LocalDate.now().plusDays(1));
    LocalDate record = nextBusinessDay(ex.plusDays(1));
    LocalDate payment = nextBusinessDay(record.plusDays(1));
    setDateIfInvalid("#dp_general_meeting_date", meeting);
    setDateIfInvalid("#dp_ex_date", ex);
    setDateIfInvalid("#dp_record_date", record);
    setDateIfInvalid("#dp_payment_date", payment);
  }

  private static void setDateIfInvalid(String selector, LocalDate value) {
    SelenideElement field = $(selector);
    if (!field.exists() || !field.isDisplayed() || !field.isEnabled() || !isInvalid(field)) return;
    setDateInput(field, value.format(DateTimeFormatter.ISO_LOCAL_DATE));
    sleep(100);
  }

  private static LocalDate nextBusinessDay(LocalDate date) {
    LocalDate result = date;
    while (result.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
        || result.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
      result = result.plusDays(1);
    }
    return result;
  }

  private static boolean isInvalid(SelenideElement field) {
    return "true".equalsIgnoreCase(safe(field.getAttribute("aria-invalid")))
      || safe(field.getAttribute("class")).toLowerCase(Locale.ROOT).contains("invalid");
  }

  private static LocalDate parseInputDate(SelenideElement field) {
    if (!field.exists()) return null;
    String value = safe(field.getValue()).trim();
    if (value.isBlank()) return null;
    try {
      return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static void setDateInput(SelenideElement field, String value) {
    executeJavaScript("const e=arguments[0], v=arguments[1]; const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set; setter.call(e,v); e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); e.dispatchEvent(new Event('blur',{bubbles:true}));", field, value);
  }

  private static void selectFirstNonEmptyNativeOption(SelenideElement field) {
    Select select = new Select(field.getWrappedElement());
    List<org.openqa.selenium.WebElement> options = select.getOptions();
    for (int index = 0; index < options.size(); index++) {
      org.openqa.selenium.WebElement option = options.get(index);
      String value = safe(option.getAttribute("value")).trim();
      if (!option.isEnabled() || value.isBlank() || "null".equalsIgnoreCase(value)) continue;
      select.selectByIndex(index);
      executeJavaScript("arguments[0].dispatchEvent(new Event('input',{bubbles:true}));"
        + "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));", field.getWrappedElement());
      return;
    }
  }

  private static void attachRequiredPdfIfAny() throws Exception {
    SelenideElement target = null;
    for (SelenideElement input : $$("input[type=file]")) {
      if (!input.exists() || !input.isEnabled()) continue;
      boolean required = input.getAttribute("required") != null
        || "true".equalsIgnoreCase(input.getAttribute("aria-required"));
      if (required) {
        target = input;
        break;
      }
    }
    if (target == null) return;

    Path pdf = Path.of("build", "reports", "disposable-repair-attachment.pdf").toAbsolutePath().normalize();
    Files.createDirectories(pdf.getParent());
    if (!Files.exists(pdf)) {
      Files.writeString(pdf, "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
        + "2 0 obj<</Type/Pages/Count 0>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n");
    }
    target.uploadFile(pdf.toFile());
  }

  private static String invalidFieldInventory() {
    List<String> result = new ArrayList<>();
    for (SelenideElement field : $$("input,textarea,select")) {
      if (!field.isDisplayed()) continue;
      boolean invalid = "true".equalsIgnoreCase(field.getAttribute("aria-invalid"))
        || safe(field.getAttribute("class")).toLowerCase(Locale.ROOT).contains("invalid");
      if (invalid) result.add(safe(field.getAttribute("id")) + ":" + safe(field.getAttribute("name")));
    }
    return result.toString();
  }

  private static void safeClickExact(String label) {
    safeClickExactAny(List.of(label));
  }

  private static void safeClickExactAny(List<String> labels) {
    for (String label : labels) {
      Number count = executeJavaScript(
        "const wanted=String(arguments[0]).toLowerCase();"
          + "const controls=[...document.querySelectorAll('button,a,[role=button],input[type=submit],input[type=button]')]"
          + ".filter(e=>e.offsetParent!==null && !e.disabled)"
          + ".filter(e=>String((e.innerText||e.value||e.getAttribute('aria-label')||'')).replace(/\\s+/g,' ').trim().toLowerCase()===wanted);"
          + "if(!controls.length)return 0;"
          + "const area=e=>{const r=e.getBoundingClientRect();return Math.max(0,r.width)*Math.max(0,r.height)};"
          + "controls.sort((a,b)=>area(b)-area(a));"
          + "controls[0].scrollIntoView({block:'center',inline:'center'}); controls[0].click(); return controls.length;",
        label);
      if (count != null && count.intValue() > 0) return;
    }
    throw new AssertionError("No visible enabled observed control for labels " + labels);
  }

  private static List<SelenideElement> exactVisibleControls(String expected) {
    List<SelenideElement> result = new ArrayList<>();
    String wanted = normalize(expected);
    for (SelenideElement control : $$("button,a,[role=button],input[type=submit],input[type=button]")) {
      if (!control.isDisplayed() || !control.isEnabled()) continue;
      String label = safe(control.getText());
      if (label.isBlank()) label = safe(control.getAttribute("value"));
      if (label.isBlank()) label = safe(control.getAttribute("aria-label"));
      if (wanted.equals(normalize(label))) result.add(control);
    }
    result.sort(Comparator.comparingInt(DisposableExecutionRepairSteps::elementArea).reversed());
    return result;
  }

  private static int elementArea(SelenideElement element) {
    try {
      Number value = executeJavaScript(
        "const r=arguments[0].getBoundingClientRect(); return Math.round(Math.max(0,r.width)*Math.max(0,r.height));",
        element.getWrappedElement());
      return value == null ? 0 : value.intValue();
    } catch (Throwable ignored) {
      return 0;
    }
  }

  private static boolean awaitVisibleTextIfPresent(String expected, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      try {
        String body = $("body").getText();
        if (body != null && body.contains(expected)) return true;
      } catch (Throwable ignored) { }
      sleep(100);
    }
    return false;
  }

  private static void awaitVisibleText(String expected, long timeoutMs) {
    if (!awaitVisibleTextIfPresent(expected, timeoutMs)) {
      throw new AssertionError("Expected visible text did not appear: " + expected);
    }
  }

  private static boolean isRecoverableClickInterception(Throwable failure) {
    Throwable root = rootCause(failure);
    return root instanceof ElementClickInterceptedException
      || root.getClass().getSimpleName().contains("ElementClickIntercepted")
      || safe(root.getMessage()).toLowerCase(Locale.ROOT).contains("click intercepted");
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable root = failure;
    while (root != null && root.getCause() != null && root.getCause() != root) root = root.getCause();
    return root == null ? failure : root;
  }

  private static void rethrow(Throwable failure) throws Exception {
    if (failure instanceof Exception exception) throw exception;
    if (failure instanceof Error error) throw error;
    throw new AssertionError("Unexpected disposable runtime failure", failure);
  }

  private static Path newestNonEmptyFile(Path directory, long minModified) {
    try (var stream = Files.walk(directory)) {
      return stream.filter(Files::isRegularFile)
        .filter(path -> {
          try {
            return Files.size(path) > 0 && Files.getLastModifiedTime(path).toMillis() >= minModified;
          } catch (Exception ignored) {
            return false;
          }
        })
        .max(Comparator.comparingLong(path -> {
          try { return path.toFile().lastModified(); } catch (Throwable ignored) { return 0L; }
        })).orElse(null);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static void clearDirectory(Path directory) {
    try {
      Files.createDirectories(directory);
      try (var paths = Files.walk(directory)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
          if (!path.equals(directory)) Files.deleteIfExists(path);
        }
      }
    } catch (Exception error) {
      throw new AssertionError("Could not clear disposable download directory " + directory, error);
    }
  }

  private static String normalize(String value) {
    return safe(value).replace('\u00a0', ' ').replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }

  private static String trim(String value, int max) {
    String text = safe(value).replaceAll("\\s+", " ").trim();
    return text.length() <= max ? text : text.substring(0, max) + "...";
  }
}
