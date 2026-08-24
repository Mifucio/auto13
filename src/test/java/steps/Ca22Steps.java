package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.sleep;
import static steps.AuthSupport.adminOpen;
import static steps.AuthSupport.sameOrigin;
import static steps.AuthSupport.selectByLabel;
import static steps.AuthSupport.submitObservedForm;
import static steps.AuthSupport.uniqueObservedControl;
import static com.codeborne.selenide.WebDriverRunner.hasWebDriverStarted;
import static com.codeborne.selenide.WebDriverRunner.url;
import static steps.RuntimeState.ADMIN_BASE_URL;

/**
 * CA-22's isolated, disposable pre-save flow.
 *
 * The feature deliberately owns unique step phrases so it cannot silently
 * reuse the generated generic actions or the semantic ledger. The fixture is
 * resolved from the classpath and uploaded only when this scenario is run.
 */
public final class Ca22Steps {
  private static final String CA22_FIXTURE_RESOURCE = "/fixtures/ca22-disposable-attachment.txt";
  private static final ThreadLocal<DisposableDraft> ACTIVE_DRAFT = new ThreadLocal<>();

  private record DisposableDraft(Path fixture) { }

  @Given("I enter the CA-22 admin flow at the observed login route")
  public void enterCa22AdminFlow() {
    adminOpen("/login");
    assertAdminPath("/login");
  }

  @And("I complete the CA-22 admin sign-in through the observed form without exposing credentials")
  public void completeCa22AdminSignIn() {
    // AuthSupport owns credential loading and never exposes credential values.
    // This step is not executed by the compile-only validation requested here.
    submitObservedForm();
  }

  @When("I open a fresh CA-22 Corporate Actions draft at the observed form route")
  public void openFreshCa22Draft() {
    adminOpen("/corporate-actions/form");
    assertAdminPath("/corporate-actions/form");
  }

  @And("I choose the observed LV company once for the unsaved CA-22 draft")
  public void chooseObservedLvCompany() {
    selectByLabel("Company", "LV");
    assertNativeSelection("Company", "LV");
  }

  @And("I choose the observed Bonus Issue form once for the unsaved CA-22 draft")
  public void chooseObservedBonusIssueForm() {
    // "bonus" is the observed option value used by the existing form helper;
    // the feature phrase keeps the user-facing form name explicit.
    selectByLabel("Corporate action form", "bonus");
    assertNativeSelection("Corporate action form", "bonus");
  }

  @And("I stage the harmless CA-22 fixture before the first save")
  public void stageHarmlessCa22Fixture() {
    Path fixture = fixturePath();
    SelenideElement input = firstFileInputIfPresent();
    if (input == null) {
      // Some renders expose the input only after the exact attachment control
      // is activated. Do not click it when an input already exists, because a
      // native file chooser is not part of this deterministic upload action.
      uniqueObservedControl("Attach file").click();
      input = awaitSingleFileInput();
    }
    input.uploadFile(fixture.toFile());
    ACTIVE_DRAFT.set(new DisposableDraft(fixture));
  }

  @Then("the unsaved CA-22 draft contains exactly one staged disposable attachment")
  public void assertOneStagedDisposableAttachment() {
    DisposableDraft draft = ACTIVE_DRAFT.get();
    if (draft == null) {
      throw new AssertionError("CA-22 attachment state was not armed before verification");
    }
    SelenideElement input = awaitSingleFileInput();
    Number fileCount = executeJavaScript(
      "const input = arguments[0]; return input.files ? input.files.length : 0;",
      input.getWrappedElement());
    if (fileCount == null || fileCount.intValue() != 1) {
      throw new AssertionError("Expected exactly one staged CA-22 fixture, found "
        + (fileCount == null ? 0 : fileCount.intValue()));
    }
    String selectedValue = input.getValue();
    String expectedName = draft.fixture().getFileName().toString();
    if (selectedValue == null || !selectedValue.toLowerCase(Locale.ROOT).endsWith(expectedName.toLowerCase(Locale.ROOT))) {
      throw new AssertionError("The staged CA-22 input did not retain the harmless fixture name");
    }
  }

  @And("I abandon the disposable CA-22 draft without saving")
  public void abandonDisposableCa22Draft() {
    try {
      abandonDraftIfArmed();
    } finally {
      ACTIVE_DRAFT.remove();
    }
  }

  /**
   * Cleanup fallback for failed assertions. It only runs after the fixture was
   * staged and only uses an exact discard control. It never clicks Save.
   */
  @After
  public void cleanupDisposableCa22Draft(Scenario scenario) {
    if (ACTIVE_DRAFT.get() == null) return;
    try {
      abandonDraftIfArmed();
    } catch (RuntimeException cleanupFailure) {
      if (scenario != null) {
        scenario.log("CA-22 disposable cleanup was not completed: " + cleanupFailure.getMessage());
      }
    } finally {
      ACTIVE_DRAFT.remove();
    }
  }

  private static Path fixturePath() {
    URL resource = Ca22Steps.class.getResource(CA22_FIXTURE_RESOURCE);
    if (resource == null) {
      throw new AssertionError("Missing harmless CA-22 fixture " + CA22_FIXTURE_RESOURCE);
    }
    try {
      Path path = Path.of(resource.toURI()).toAbsolutePath();
      if (!Files.isRegularFile(path) || Files.size(path) == 0) {
        throw new AssertionError("Harmless CA-22 fixture is missing or empty: " + path);
      }
      return path;
    } catch (URISyntaxException | IOException error) {
      throw new AssertionError("Could not resolve harmless CA-22 fixture", error);
    }
  }

  private static SelenideElement firstFileInputIfPresent() {
    ElementsCollection inputs = allFileInputs();
    if (inputs.size() > 1) {
      throw new AssertionError("Expected at most one file input for the CA-22 draft, found " + inputs.size());
    }
    return inputs.isEmpty() ? null : inputs.get(0);
  }

  private static SelenideElement awaitSingleFileInput() {
    long deadline = System.currentTimeMillis() + Math.max(Configuration.timeout, 10000);
    while (System.currentTimeMillis() < deadline) {
      SelenideElement input = firstFileInputIfPresent();
      if (input != null) return input;
      sleep(100);
    }
    throw new AssertionError("The CA-22 draft did not expose exactly one file input");
  }

  private static ElementsCollection allFileInputs() {
    return $("body").$$("input[type='file']");
  }

  private static void assertNativeSelection(String label, String expectedValue) {
    SelenideElement control = $("select[aria-label='" + label + "'], select[name='" + label + "']");
    if (control.exists() && !expectedValue.equals(control.getValue())) {
      throw new AssertionError("Observed " + label + " selection was not " + expectedValue);
    }
  }

  private static void abandonDraftIfArmed() {
    if (ACTIVE_DRAFT.get() == null || !hasWebDriverStarted()) return;
    SelenideElement discard = awaitUniqueDiscardControl();
    discard.click();
    awaitRouteLeavingDraft();
  }

  private static SelenideElement awaitUniqueDiscardControl() {
    long deadline = System.currentTimeMillis() + Math.max(Configuration.timeout, 10000);
    while (System.currentTimeMillis() < deadline) {
      List<SelenideElement> matches = discardControls();
      if (matches.size() == 1) return matches.get(0);
      if (matches.size() > 1) {
        throw new AssertionError("Expected exactly one safe CA-22 discard control, found " + matches.size());
      }
      sleep(100);
    }
    throw new AssertionError("No safe CA-22 discard control was visible; the draft was not saved");
  }

  private static List<SelenideElement> discardControls() {
    List<SelenideElement> matches = new ArrayList<>();
    for (SelenideElement candidate : $("body").$$("button, a, input[type=button], input[type=submit], [role=button]")) {
      if (!candidate.isDisplayed() || !candidate.isEnabled()) continue;
      String text = normalize(candidate.getText());
      String aria = normalize(candidate.getAttribute("aria-label"));
      String title = normalize(candidate.getAttribute("title"));
      if (isDiscardLabel(text) || isDiscardLabel(aria) || isDiscardLabel(title)) matches.add(candidate);
    }
    return matches;
  }

  private static boolean isDiscardLabel(String value) {
    return value.equals("Cancel") || value.equals("Discard") || value.equals("Discard changes");
  }

  private static void awaitRouteLeavingDraft() {
    long deadline = System.currentTimeMillis() + Math.max(Configuration.timeout, 10000);
    while (System.currentTimeMillis() < deadline) {
      if (!isAdminPath("/corporate-actions/form")) return;
      sleep(100);
    }
    throw new AssertionError("CA-22 discard did not leave the unsaved form route");
  }

  private static void assertAdminPath(String expectedPath) {
    String current = url();
    if (!sameOrigin(current, ADMIN_BASE_URL)) {
      throw new AssertionError("Expected the CA-22 admin origin, got " + current);
    }
    if (!pathMatches(current, expectedPath)) {
      throw new AssertionError("Expected CA-22 admin route " + expectedPath + ", got " + current);
    }
  }

  private static boolean isAdminPath(String expectedPath) {
    String current = url();
    return sameOrigin(current, ADMIN_BASE_URL) && pathMatches(current, expectedPath);
  }

  private static boolean pathMatches(String currentUrl, String expectedPath) {
    if (currentUrl == null) return false;
    try {
      String path = URI.create(currentUrl).getPath();
      return expectedPath.equals(path) || (expectedPath + "/").equals(path);
    } catch (IllegalArgumentException invalidUrl) {
      return false;
    }
  }

  private static String normalize(String value) {
    return value == null ? "" : value.replaceAll("\\s+", " ").trim();
  }
}
