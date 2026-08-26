package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.FileDownloadMode;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.WebElement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.refresh;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.WebDriverRunner.getWebDriver;
import static com.codeborne.selenide.WebDriverRunner.hasWebDriverStarted;
import static com.codeborne.selenide.WebDriverRunner.url;
import static steps.RuntimeState.BASE_URL;

/**
 * Creates disposable application data through the real customer surface and
 * then opens that exact numeric application in the admin surface. Admin UI does
 * not expose Create Application, so admin mutation tests must not fabricate an
 * admin-side creation path.
 */
public final class CrossSurfaceDisposableAdminSteps {
  private static final String TYPE = "Bonus Issue";
  private static final String FIXTURE_NAME = "ca22-disposable-attachment.txt";
  private static final Pattern APPLICATION_ID = Pattern.compile("/corporate-actions/application-form/(\\d+)(?:[/?#].*)?$");
  private static final Path CA24_DOWNLOADS = Path.of("build", "ca24-disposable-downloads").toAbsolutePath().normalize();

  private final DisposableScenarioPrerequisites customer;
  private final AdminSteps admin;
  private final AdminMutationRepairSteps mutations;

  private String applicationId;
  private Path downloadedAttachment;

  public CrossSurfaceDisposableAdminSteps(DisposableScenarioPrerequisites customer,
                                          AdminSteps admin,
                                          AdminMutationRepairSteps mutations) {
    this.customer = customer;
    this.admin = admin;
    this.mutations = mutations;
  }

  @Given("a fresh disposable customer Bonus Issue draft is opened in the admin application")
  public void freshCustomerDraftOpenedInAdmin() throws Exception {
    createCustomerDraft(false);
  }

  @Given("a fresh disposable customer Bonus Issue draft with a persisted attachment is opened in the admin application")
  public void freshCustomerDraftWithAttachmentOpenedInAdmin() throws Exception {
    createCustomerDraft(true);
  }

  private void createCustomerDraft(boolean withAttachment) throws Exception {
    // Cross-surface hooks start [admin] scenarios on the admin origin. Force
    // the customer origin before the customer prerequisite performs login and
    // form creation so chooser retries never inherit the admin route.
    open(BASE_URL);
    customer.freshSavedDisposableApplication(TYPE);
    applicationId = applicationIdFromUrl(url());
    if (applicationId == null) {
      throw new AssertionError("Disposable customer draft did not expose a numeric application ID; url=" + url());
    }

    if (withAttachment) {
      mutations.attachHarmlessFixture();
      mutations.attachmentVisible();
      refresh();
      openTab("Attachments");
      waitForVisibleText(FIXTURE_NAME, 10000);
    }

    clearBrowserAuthenticationState();
    admin.i_am_authenticated_in_the_admin_application();
    admin.i_navigate_to_the_admin_string("/corporate-actions/application-form/" + applicationId);
    awaitAdminDetail();
  }

  @When("I download the persisted disposable attachment from the admin application")
  public void downloadPersistedDisposableAttachment() {
    awaitAdminDetail();
    openTab("Attachments");
    waitForVisibleText(FIXTURE_NAME, 10000);
    SelenideElement control = attachmentDownloadControl();
    clearDirectory(CA24_DOWNLOADS);

    String previousFolder = Configuration.downloadsFolder;
    FileDownloadMode previousMode = Configuration.fileDownload;
    java.io.File returned = null;
    Throwable downloadFailure = null;
    try {
      Configuration.downloadsFolder = CA24_DOWNLOADS.toString();
      Configuration.fileDownload = FileDownloadMode.FOLDER;
      try {
        returned = control.download();
      } catch (Throwable failure) {
        downloadFailure = failure;
        try { control.click(); } catch (Throwable ignored) { }
      }
      long deadline = System.currentTimeMillis() + 7000;
      while (System.currentTimeMillis() < deadline) {
        Path file = firstNonEmptyFile(CA24_DOWNLOADS);
        if (file != null) {
          downloadedAttachment = file;
          return;
        }
        sleep(100);
      }
    } finally {
      Configuration.downloadsFolder = previousFolder;
      Configuration.fileDownload = previousMode;
    }
    if (returned != null && returned.isFile() && returned.length() > 0) {
      downloadedAttachment = returned.toPath().toAbsolutePath().normalize();
      return;
    }
    throw new AssertionError("CA-24 known disposable attachment produced no non-empty download; failure="
      + (downloadFailure == null ? "none" : downloadFailure.getClass().getSimpleName()));
  }

  @Then("the persisted disposable attachment download exists")
  public void persistedDisposableAttachmentDownloadExists() {
    if (downloadedAttachment == null || !Files.isRegularFile(downloadedAttachment)) {
      throw new AssertionError("CA-24 did not record a downloaded disposable attachment");
    }
    try {
      if (Files.size(downloadedAttachment) <= 0) {
        throw new AssertionError("CA-24 downloaded attachment is empty: " + downloadedAttachment);
      }
    } catch (java.io.IOException error) {
      throw new AssertionError("CA-24 could not inspect downloaded attachment", error);
    }
  }

  @After(value = "@cross_surface_disposable_admin", order = 2000)
  public void cleanupDisposableApplication(Scenario scenario) {
    if (applicationId == null || !hasWebDriverStarted()) return;
    try {
      if (!isCurrentApplicationDetail()) {
        admin.i_am_authenticated_in_the_admin_application();
        admin.i_navigate_to_the_admin_string("/corporate-actions/application-form/" + applicationId);
        awaitAdminDetail();
      }
      if (!deleteCurrentApplication()) {
        scenario.log("Disposable cleanup warning: exact Delete control is unavailable for application " + applicationId);
      }
    } catch (Throwable cleanupFailure) {
      scenario.log("Disposable cleanup warning for application " + applicationId + ": " + cleanupFailure.getMessage());
    } finally {
      applicationId = null;
      downloadedAttachment = null;
    }
  }

  private void awaitAdminDetail() {
    long deadline = System.currentTimeMillis() + Math.max(Configuration.timeout, RuntimeState.HANG_TIMEOUT_MS);
    String lastBody = "";
    while (System.currentTimeMillis() < deadline) {
      if (isCurrentApplicationDetail()) {
        lastBody = $("body").shouldBe(visible).getText();
        // The exact numeric route is authoritative. On a busy live backend the
        // localized form title can arrive later than the reusable detail tabs,
        // so accept either marker instead of failing a healthy rendered page.
        if ((lastBody != null && lastBody.contains(TYPE))
            || CorporateActionsTabProbe.findClickable("Attachments") != null) return;
      }
      sleep(100);
    }
    throw new AssertionError("Admin surface did not open disposable application " + applicationId
      + "; url=" + url() + " body=" + clean(lastBody).substring(0, Math.min(clean(lastBody).length(), 800)));
  }

  private boolean isCurrentApplicationDetail() {
    String current = url();
    return current != null && applicationId != null
      && current.contains("/corporate-actions/application-form/" + applicationId)
      && !current.contains("/login");
  }

  private static void openTab(String name) {
    if (CorporateActionsTabProbe.isActive(name)) return;
    WebElement clickable = CorporateActionsTabProbe.findClickable(name);
    if (clickable == null) throw new AssertionError("No observed " + name + " tab control was found");
    CorporateActionsTabProbe.prepare(name);
    $(clickable).scrollIntoView("{block:'center',inline:'center'}").click();
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 15000);
    while (System.currentTimeMillis() < deadline) {
      if (CorporateActionsTabProbe.isActive(name)) return;
      sleep(100);
    }
    throw new AssertionError(name + " tab never became active");
  }

  private static void waitForVisibleText(String expected, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      String body = $("body").shouldBe(visible).getText();
      if (body != null && body.contains(expected)) return;
      sleep(100);
    }
    throw new AssertionError("Expected visible text did not appear: " + expected);
  }

  private static SelenideElement attachmentDownloadControl() {
    List<SelenideElement> containers = new ArrayList<>();
    for (SelenideElement candidate : $$("tr,li,.card,.row,div")) {
      if (!candidate.isDisplayed()) continue;
      String text = clean(candidate.getText());
      if (text.contains(FIXTURE_NAME)) containers.add(candidate);
    }
    containers.sort(Comparator.comparingInt(candidate -> clean(candidate.getText()).length()));
    for (SelenideElement container : containers) {
      for (SelenideElement control : container.$$("a,button,[role=button]")) {
        if (!control.isDisplayed() || !control.isEnabled()) continue;
        String label = lower(clean(control.getText()) + " " + clean(control.getAttribute("aria-label"))
          + " " + clean(control.getAttribute("title")));
        String href = clean(control.getAttribute("href"));
        if (label.contains("download") || (!href.isBlank() && "a".equalsIgnoreCase(control.getTagName()))) return control;
      }
    }
    throw new AssertionError("CA-24 persisted fixture is visible but no row-scoped download control was found");
  }

  private boolean deleteCurrentApplication() {
    List<SelenideElement> deletes = exactVisibleControls("Delete");
    if (deletes.size() != 1) return false;
    deletes.get(0).click();
    sleep(200);
    List<SelenideElement> dialogs = new ArrayList<>();
    for (SelenideElement dialog : $$("[role=dialog],ngb-modal-window,.modal")) {
      if (dialog.isDisplayed()) dialogs.add(dialog);
    }
    if (!dialogs.isEmpty()) {
      List<SelenideElement> confirmations = new ArrayList<>();
      for (SelenideElement control : dialogs.get(0).$$("button,a,[role=button]")) {
        if (!control.isDisplayed() || !control.isEnabled()) continue;
        String label = lower(control.getText());
        if (label.equals("delete") || label.equals("confirm") || label.contains("confirm deletion")) confirmations.add(control);
      }
      if (confirmations.size() == 1) confirmations.get(0).click();
    }
    long deadline = System.currentTimeMillis() + 10000;
    while (System.currentTimeMillis() < deadline) {
      if (!isCurrentApplicationDetail()) return true;
      sleep(100);
    }
    return false;
  }

  private static List<SelenideElement> exactVisibleControls(String label) {
    List<SelenideElement> result = new ArrayList<>();
    for (SelenideElement control : $$("button,a,[role=button],input[type=button],input[type=submit]")) {
      if (!control.isDisplayed() || !control.isEnabled()) continue;
      String observed = clean(control.getText());
      if (observed.isBlank()) observed = clean(control.getAttribute("value"));
      if (observed.isBlank()) observed = clean(control.getAttribute("aria-label"));
      if (label.equalsIgnoreCase(observed)) result.add(control);
    }
    return result;
  }

  private static String applicationIdFromUrl(String current) {
    if (current == null) return null;
    Matcher matcher = APPLICATION_ID.matcher(current);
    return matcher.find() ? matcher.group(1) : null;
  }

  private static Path firstNonEmptyFile(Path directory) {
    try (var stream = Files.walk(directory)) {
      return stream.filter(Files::isRegularFile).filter(path -> path.toFile().length() > 0).findFirst().orElse(null);
    } catch (java.io.IOException ignored) {
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
    } catch (java.io.IOException error) {
      throw new AssertionError("Could not clear download directory " + directory, error);
    }
  }

  private static void clearBrowserAuthenticationState() {
    if (!hasWebDriverStarted()) return;
    try { executeJavaScript("try{localStorage.clear()}catch(e){} try{sessionStorage.clear()}catch(e){}"); } catch (Throwable ignored) { }
    try { getWebDriver().manage().deleteAllCookies(); } catch (Throwable ignored) { }
  }

  private static String clean(String value) {
    return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
  }

  private static String lower(String value) {
    return clean(value).toLowerCase(Locale.ROOT);
  }
}
