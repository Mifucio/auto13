package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.After;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.screenshot;
import static com.codeborne.selenide.Selenide.sleep;
import static steps.AuthSupport.adminLogin;
import static steps.AuthSupport.selectByLabel;
import static steps.AuthSupport.sameOrigin;
import static steps.AuthSupport.uniqueObservedControl;
import static steps.AuthSupport.awaitAuthenticatedAdmin;
import static steps.RuntimeState.ADMIN_BASE_URL;

/**
 * CA-23's only data-changing flow.  This class deliberately does not reuse
 * generic action phrases: every step proves the disposable boundary before it
 * can reach the save control.
 */
public final class Ca23Steps {

  private static final String CORPORATE_ACTIONS_PATH = "/corporate-actions";
  private static final String CREATION_PATH = "/corporate-actions/form";
  private static final String OBSERVED_COMPANY = "LV";
  private static final String OBSERVED_FORM = "bonus";
  private static final String DETERMINISTIC_MARKER = "CA23_DISPOSABLE_DRAFT_20260817";
  private static final Pattern APPLICATION_DETAIL_PATH =
    Pattern.compile("^/corporate-actions/application-form/(\\d+)(?:/.*)?$");
  private static final List<String> APPLICATION_STATUSES = List.of(
    "Draft", "Invalid", "Awaiting signatures", "Submitted", "Inserted",
    "Insertion issue", "Deleted", "In progress", "Completed", "Cancelled",
    "Reversed");

  private boolean cleanupContractProven;
  private boolean saveAttempted;
  private String marker;
  private String savedApplicationId;

  @Given("the CA-23 disposable Draft starts from the observed admin session")
  public void start_from_observed_admin_session() {
    adminOpen(CORPORATE_ACTIONS_PATH);
    if (isAuthenticatedAdminPage()) return;

    if (!isAdminLoginPage()) {
      throw new AssertionError("CA-23 requires the observed admin login or an authenticated admin page; url="
        + currentUrl());
    }
    adminLogin();
    awaitAuthenticatedAdmin();
  }

  @And("the CA-23 cleanup preflight opens the admin Corporate Actions list")
  public void open_cleanup_preflight_list() {
    adminOpen(CORPORATE_ACTIONS_PATH);
    requireCorporateActionsList();
  }

  @And("the CA-23 cleanup contract is proven before any save")
  public void prove_cleanup_contract_before_save() {
    if (saveAttempted) {
      throw new AssertionError("CA-23 cleanup preflight must complete before the save action");
    }
    requireCorporateActionsList();

    List<SelenideElement> rows = visibleApplicationRows();
    if (rows.isEmpty()) {
      throw new AssertionError(
        "CA-23 fail-closed before save: the live Corporate Actions UI did not expose an observable "
          + "Corporate Actions row from which disposable cleanup can be proven; rows=" + rowInventory(rows));
    }

    List<SelenideElement> markerRows = new ArrayList<>();
    for (SelenideElement row : rows) {
      if (normalize(row.getText()).contains(normalize(DETERMINISTIC_MARKER))) markerRows.add(row);
    }
    if (!markerRows.isEmpty()) {
      throw new AssertionError("CA-23 fail-closed before save: deterministic marker already exists in the live "
        + "list, so creating another application would be ambiguous; rows=" + rowInventory(markerRows));
    }

    for (SelenideElement row : rows) {
      List<SelenideElement> rowControls = applicationDeleteControls(List.of(row));
      if (rowControls.size() != 1) {
        throw new AssertionError(
          "CA-23 fail-closed before save: every observable application row must expose exactly one "
            + "row-scoped enabled Delete control; row=" + rowInventory(List.of(row))
            + "; deleteControls=" + controlInventory(rowControls));
      }
    }

    cleanupContractProven = true;
    screenshot("ca23-cleanup-contract-before-save");
  }

  @When("I open the observed CA-23 creation surface without saving")
  public void open_creation_surface_without_saving() {
    requireCleanupContract();
    requireCorporateActionsList();

    SelenideElement create = uniqueObservedControl("Create Application");
    create.click();
    SelenideElement modalHeading = awaitExactVisibleText("Choose application type");
    SelenideElement modal = modalHeading.closest("ngb-modal-window, [role=dialog], .modal");
    if (!modal.exists()) {
      throw new AssertionError("CA-23 creation type landmark did not expose an observed modal container");
    }
    List<SelenideElement> bonusOptions = exactVisibleDescendants(modal, "Bonus Issue");
    if (bonusOptions.size() != 1) {
      throw new AssertionError("CA-23 expected exactly one observed Bonus Issue option in the creation modal, found "
        + bonusOptions.size() + "; options=" + elementInventory(bonusOptions));
    }
    bonusOptions.get(0).click();
    awaitCreationRoute();
  }

  @And("I choose the observed CA-23 company {string}")
  public void choose_observed_company(String company) {
    requireCreationForm();
    if (!OBSERVED_COMPANY.equals(company)) {
      throw new AssertionError("CA-23 only permits the observed disposable company " + OBSERVED_COMPANY
        + "; received " + company);
    }
    selectByLabel("Company", company);
  }

  @And("I choose the observed CA-23 form {string}")
  public void choose_observed_form(String form) {
    requireCreationForm();
    if (!OBSERVED_FORM.equals(form)) {
      throw new AssertionError("CA-23 only permits the observed disposable form " + OBSERVED_FORM
        + "; received " + form);
    }
    selectByLabel("Corporate action form", form);
  }

  @And("the CA-23 draft form receives the deterministic marker {string}")
  public void enter_deterministic_marker(String expectedMarker) {
    requireCreationForm();
    if (!DETERMINISTIC_MARKER.equals(expectedMarker)) {
      throw new AssertionError("CA-23 marker is not deterministic or not the approved disposable marker: "
        + expectedMarker);
    }

    List<SelenideElement> fields = exactMarkerFields();
    if (fields.size() != 1) {
      throw new AssertionError("CA-23 fail-closed before save: expected exactly one observed visible Form field "
        + "for the deterministic marker, found " + fields.size() + "; fields=" + elementInventory(fields));
    }
    fields.get(0).setValue(expectedMarker);
    if (!expectedMarker.equals(fields.get(0).val())) {
      throw new AssertionError("CA-23 marker was not retained by the observed Form field");
    }
    marker = expectedMarker;
  }

  @And("I save exactly one CA-23 disposable Draft")
  public void save_exactly_one_disposable_draft() {
    requireCleanupContract();
    requireCreationForm();
    if (!DETERMINISTIC_MARKER.equals(marker)) {
      throw new AssertionError("CA-23 refuses to save without the deterministic marker");
    }

    SelenideElement save = uniqueObservedControl("Save application");
    saveAttempted = true;
    save.click();
    awaitSavedApplicationRoute();
    savedApplicationId = applicationIdFromCurrentUrl();
    if (savedApplicationId == null) {
      throw new AssertionError("CA-23 save did not expose a stable numeric application identity; url="
        + currentUrl());
    }
  }

  @Then("the saved CA-23 application proves marker {string} and status {string}")
  public void assert_saved_marker_and_status(String expectedMarker, String expectedStatus) {
    requireSavedApplication();
    if (!DETERMINISTIC_MARKER.equals(expectedMarker) || !"Draft".equals(expectedStatus)) {
      throw new AssertionError("CA-23 assertion received an unapproved marker or status");
    }
    if (!visiblePageContainsValue(expectedMarker)) {
      throw new AssertionError("CA-23 saved application did not visibly retain marker " + expectedMarker);
    }
    List<SelenideElement> statusMatches = exactVisibleText(expectedStatus);
    if (statusMatches.size() != 1) {
      throw new AssertionError("CA-23 saved application did not expose exactly one visible Draft status; found "
        + statusMatches.size() + "; matches=" + elementInventory(statusMatches));
    }
    screenshot("ca23-disposable-draft-saved");
  }

  @And("I delete only the saved CA-23 disposable Draft")
  public void delete_only_saved_disposable_draft() {
    requireSavedApplication();
    deleteSavedApplication();
    savedApplicationId = null;
    saveAttempted = false;
  }

  @And("I reset the CA-23 browser session after cleanup")
  public void reset_browser_session_after_cleanup() {
    resetBrowserSession();
  }

  /** Always runs, including when a post-save assertion fails. */
  @After(value = "@direct_ca_disposable_draft", order = 1000)
  public void mandatory_cleanup_and_reset() {
    AssertionError cleanupFailure = null;
    try {
      if (saveAttempted && marker != null) {
        deleteSavedApplication();
        savedApplicationId = null;
        saveAttempted = false;
      }
    } catch (Throwable error) {
      cleanupFailure = new AssertionError("CA-23 mandatory cleanup failed closed; the disposable marker may remain: "
        + error.getMessage(), error);
    } finally {
      try {
        resetBrowserSession();
      } catch (Throwable resetError) {
        if (cleanupFailure == null) {
          cleanupFailure = new AssertionError("CA-23 browser reset failed after cleanup", resetError);
        } else {
          cleanupFailure.addSuppressed(resetError);
        }
      }
    }
    if (cleanupFailure != null) throw cleanupFailure;
  }

  private void requireCleanupContract() {
    if (!cleanupContractProven) {
      throw new AssertionError("CA-23 refuses to mutate data before the safe cleanup contract is proven");
    }
  }

  private void requireCreationForm() {
    if (!sameOrigin(currentUrl(), ADMIN_BASE_URL) || !CREATION_PATH.equals(pathOf(currentUrl()))) {
      throw new AssertionError("CA-23 expected the observed creation form route " + CREATION_PATH
        + "; url=" + currentUrl());
    }
    $("form").shouldBe(visible);
  }

  private void requireSavedApplication() {
    if (marker == null || savedApplicationId == null || applicationIdFromCurrentUrl() == null) {
      throw new AssertionError("CA-23 requires one saved application with a stable numeric identity");
    }
    if (!Objects.equals(savedApplicationId, applicationIdFromCurrentUrl())) {
      throw new AssertionError("CA-23 current detail identity changed from " + savedApplicationId
        + " to " + applicationIdFromCurrentUrl());
    }
  }

  private void deleteSavedApplication() {
    if (marker == null) {
      throw new AssertionError("CA-23 cannot delete without its deterministic marker");
    }

    if (applicationIdFromCurrentUrl() == null) {
      adminOpen(CORPORATE_ACTIONS_PATH);
      requireCorporateActionsList();
      List<SelenideElement> markerRows = new ArrayList<>();
      for (SelenideElement row : visibleApplicationRows()) {
        if (normalize(row.getText()).contains(normalize(marker))) markerRows.add(row);
      }
      if (markerRows.size() != 1) {
        throw new AssertionError("CA-23 cleanup expected exactly one visible row for marker " + marker
          + "; found " + markerRows.size() + "; rows=" + rowInventory(markerRows));
      }
      List<SelenideElement> controls = applicationDeleteControls(markerRows);
      if (controls.size() != 1) {
        throw new AssertionError("CA-23 cleanup refused an ambiguous row Delete control; controls="
          + controlInventory(controls));
      }
      controls.get(0).click();
    } else {
      if (!visiblePageContainsValue(marker)) {
        throw new AssertionError("CA-23 cleanup refused to delete a detail page without the exact marker " + marker);
      }
      List<SelenideElement> controls = exactVisibleControls("Delete");
      if (controls.size() != 1) {
        throw new AssertionError("CA-23 cleanup refused an ambiguous or unavailable detail Delete control; controls="
          + controlInventory(controls));
      }
      controls.get(0).click();
    }

    confirmDeleteIfPresented();
    awaitDeletedMarkerAbsent();
    screenshot("ca23-disposable-draft-cleaned");
  }

  private void confirmDeleteIfPresented() {
    List<SelenideElement> dialogs = visibleElements("[role=dialog], ngb-modal-window, .modal");
    if (dialogs.size() > 1) {
      throw new AssertionError("CA-23 cleanup exposed more than one visible delete confirmation dialog");
    }
    if (dialogs.isEmpty()) return;

    List<SelenideElement> confirmations = new ArrayList<>();
    for (SelenideElement dialog : dialogs) {
      for (SelenideElement control : dialog.$$("button, a, input[type=button], input[type=submit], [role=button]")) {
        if (!control.isDisplayed() || !control.isEnabled()) continue;
        String label = normalize(controlLabel(control));
        if ("delete".equals(label) || "confirm".equals(label) || "confirm deletion".equals(label)) {
          confirmations.add(control);
        }
      }
    }
    if (confirmations.size() != 1) {
      throw new AssertionError("CA-23 cleanup expected exactly one visible confirmation control, found "
        + confirmations.size() + "; controls=" + controlInventory(confirmations));
    }
    confirmations.get(0).click();
  }

  private void awaitDeletedMarkerAbsent() {
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      if (CORPORATE_ACTIONS_PATH.equals(pathOf(currentUrl())) && !visiblePageContainsValue(marker)) {
        return;
      }
      sleep(100);
    }
    throw new AssertionError("CA-23 cleanup did not return to the Corporate Actions list without marker " + marker
      + "; url=" + currentUrl());
  }

  private void awaitCreationRoute() {
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      if (CREATION_PATH.equals(pathOf(currentUrl()))) return;
      sleep(100);
    }
    throw new AssertionError("CA-23 creation control did not reach the observed form route; url=" + currentUrl());
  }

  private void awaitSavedApplicationRoute() {
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      if (applicationIdFromCurrentUrl() != null) return;
      sleep(100);
    }
    throw new AssertionError("CA-23 save did not reach a numeric application detail route; url=" + currentUrl());
  }

  private void resetBrowserSession() {
    if (!com.codeborne.selenide.WebDriverRunner.hasWebDriverStarted()) return;
    Throwable cookieFailure = null;
    try {
      com.codeborne.selenide.WebDriverRunner.getWebDriver().manage().deleteAllCookies();
    } catch (Throwable error) {
      cookieFailure = error;
    }
    try {
      executeJavaScript("window.localStorage.clear(); window.sessionStorage.clear();");
    } catch (Throwable error) {
      throw new AssertionError("CA-23 could not clear browser storage during reset", error);
    }
    if (cookieFailure != null) {
      throw new AssertionError("CA-23 could not clear browser cookies during reset", cookieFailure);
    }
  }

  private void adminOpen(String path) {
    if (ADMIN_BASE_URL == null || ADMIN_BASE_URL.isBlank()) {
      throw new AssertionError("CA-23 admin origin is not configured");
    }
    open(ADMIN_BASE_URL + path);
  }

  private void requireCorporateActionsList() {
    if (!sameOrigin(currentUrl(), ADMIN_BASE_URL) || !CORPORATE_ACTIONS_PATH.equals(pathOf(currentUrl()))) {
      throw new AssertionError("CA-23 expected the admin Corporate Actions route; url=" + currentUrl());
    }
    $("h1").shouldBe(visible).shouldHave(text("Corporate Actions"));
    $("#formNames").shouldBe(visible);
    uniqueObservedControl("Apply filters");
  }

  private boolean isAuthenticatedAdminPage() {
    String url = currentUrl();
    if (url == null || url.contains("/login")) return false;
    try {
      String body = $("body").shouldBe(visible).getText().toLowerCase(Locale.ROOT);
      return sameOrigin(url, ADMIN_BASE_URL)
        && (body.contains("corporate actions") || body.contains("management") || body.contains("welcome back"));
    } catch (Throwable ignored) {
      return false;
    }
  }

  private boolean isAdminLoginPage() {
    return sameOrigin(currentUrl(), ADMIN_BASE_URL) && currentUrl().contains("/login");
  }

  private List<SelenideElement> visibleApplicationRows() {
    List<SelenideElement> rows = new ArrayList<>();
    for (SelenideElement row : $("body").$$("table tbody tr")) {
      if (!row.isDisplayed()) continue;
      ElementsCollection cells = row.$$("td");
      String rowText = normalize(row.getText());
      boolean statusRow = APPLICATION_STATUSES.stream().anyMatch(status -> rowText.contains(normalize(status)));
      if (cells.size() >= 4 && statusRow) rows.add(row);
    }
    return rows;
  }

  private List<SelenideElement> applicationDeleteControls(List<SelenideElement> rows) {
    List<SelenideElement> controls = new ArrayList<>();
    for (SelenideElement row : rows) {
      for (SelenideElement control : row.$$("button, a, input[type=button], input[type=submit], [role=button]")) {
        if (control.isDisplayed() && control.isEnabled() && "delete".equals(normalize(controlLabel(control)))) {
          controls.add(control);
        }
      }
    }
    return controls;
  }

  private List<SelenideElement> exactMarkerFields() {
    List<SelenideElement> fields = new ArrayList<>();
    for (SelenideElement field : $("body").$$("input:not([type=hidden]), textarea, [contenteditable=true]")) {
      if (!field.isDisplayed() || !field.isEnabled()) continue;
      if (hasExactFieldLabel(field, "Form")) fields.add(field);
    }
    return fields;
  }

  private boolean hasExactFieldLabel(SelenideElement field, String label) {
    for (String attribute : List.of("aria-label", "name", "placeholder")) {
      if (label.equals(field.getAttribute(attribute))) return true;
    }
    try {
      SelenideElement parentLabel = field.closest("label");
      return parentLabel.exists() && normalize(label).equals(normalize(parentLabel.getText()));
    } catch (Throwable ignored) {
      return false;
    }
  }

  private List<SelenideElement> exactVisibleControls(String label) {
    List<SelenideElement> matches = new ArrayList<>();
    for (SelenideElement control : $("body").$$("button, a, input[type=button], input[type=submit], [role=button]")) {
      if (control.isDisplayed() && control.isEnabled() && normalize(label).equals(normalize(controlLabel(control)))) {
        matches.add(control);
      }
    }
    return matches;
  }

  private List<SelenideElement> visibleElements(String selector) {
    List<SelenideElement> result = new ArrayList<>();
    for (SelenideElement element : $("body").$$(selector)) {
      if (element.isDisplayed()) result.add(element);
    }
    return result;
  }

  private SelenideElement awaitExactVisibleText(String expected) {
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      List<SelenideElement> matches = exactVisibleText(expected);
      if (matches.size() == 1) return matches.get(0);
      if (matches.size() > 1) {
        throw new AssertionError("CA-23 expected exactly one visible " + expected + " landmark, found "
          + matches.size());
      }
      sleep(100);
    }
    throw new AssertionError("CA-23 did not render visible " + expected);
  }

  private List<SelenideElement> exactVisibleText(String expected) {
    List<SelenideElement> matches = new ArrayList<>();
    for (SelenideElement element : $("body").$$("*")) {
      if (element.isDisplayed() && expected.equals(element.getText().trim())) matches.add(element);
    }
    return leafElements(matches);
  }

  private List<SelenideElement> exactVisibleDescendants(SelenideElement root, String expected) {
    List<SelenideElement> matches = new ArrayList<>();
    for (SelenideElement element : root.$$("*")) {
      if (element.isDisplayed() && expected.equals(element.getText().trim())) matches.add(element);
    }
    return leafElements(matches);
  }

  private List<SelenideElement> leafElements(List<SelenideElement> candidates) {
    List<SelenideElement> leaves = new ArrayList<>();
    for (SelenideElement candidate : candidates) {
      boolean containsSameTextChild = false;
      for (SelenideElement child : candidate.$$("*")) {
        if (child.isDisplayed() && candidate.getText().trim().equals(child.getText().trim())) {
          containsSameTextChild = true;
          break;
        }
      }
      if (!containsSameTextChild) leaves.add(candidate);
    }
    return leaves;
  }

  private boolean visiblePageContainsValue(String value) {
    if (value == null || value.isBlank()) return false;
    String body = $("body").shouldBe(visible).getText();
    if (body != null && body.contains(value)) return true;
    for (SelenideElement field : $("body").$$("input, textarea, [contenteditable=true]")) {
      if (!field.isDisplayed()) continue;
      String fieldValue = field.val();
      if (value.equals(fieldValue) || (fieldValue != null && fieldValue.contains(value))) return true;
    }
    return false;
  }

  private String applicationIdFromCurrentUrl() {
    String path = pathOf(currentUrl());
    if (path == null) return null;
    Matcher matcher = APPLICATION_DETAIL_PATH.matcher(path);
    return matcher.matches() ? matcher.group(1) : null;
  }

  private String currentUrl() {
    return com.codeborne.selenide.WebDriverRunner.hasWebDriverStarted()
      ? com.codeborne.selenide.WebDriverRunner.url() : null;
  }

  private String pathOf(String url) {
    if (url == null || url.isBlank()) return null;
    try {
      return URI.create(url).getPath();
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private String controlLabel(SelenideElement control) {
    String textValue = control.getText();
    if (textValue != null && !textValue.isBlank()) return textValue;
    for (String attribute : List.of("aria-label", "title", "value")) {
      String value = control.getAttribute(attribute);
      if (value != null && !value.isBlank()) return value;
    }
    return "";
  }

  private String normalize(String value) {
    return value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
  }

  private String elementInventory(List<SelenideElement> elements) {
    List<String> inventory = new ArrayList<>();
    for (SelenideElement element : elements) {
      inventory.add(element.getTagName() + "[text=" + element.getText()
        + ",aria=" + element.getAttribute("aria-label") + "]");
    }
    return inventory.toString();
  }

  private String controlInventory(List<SelenideElement> elements) {
    List<String> inventory = new ArrayList<>();
    for (SelenideElement element : elements) {
      inventory.add(element.getTagName() + "[label=" + controlLabel(element) + "]");
    }
    return inventory.toString();
  }

  private String rowInventory(List<SelenideElement> rows) {
    List<String> inventory = new ArrayList<>();
    for (SelenideElement row : rows) inventory.add(normalize(row.getText()));
    return inventory.toString();
  }
}
