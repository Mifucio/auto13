package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.WebDriverRunner.url;

/**
 * Repair steps for the data-changing admin scenarios. Every scenario using
 * these steps starts from a disposable CA-23 draft and carries the
 * @direct_ca_disposable_draft tag, so Ca23Steps deletes the application after
 * the scenario instead of mutating the shared LV/Bonus read-only fixture.
 */
public final class AdminMutationRepairSteps {
  private static final String MARKER = "CA23_DISPOSABLE_DRAFT_20260817";
  private static final String FIXTURE = "/fixtures/ca22-disposable-attachment.txt";

  private final Ca23Steps draft;
  private String selectedInternalUser = "";
  private String assignmentBodyBefore = "";
  private String attachmentName = "";

  public AdminMutationRepairSteps(Ca23Steps draft) {
    this.draft = draft;
  }

  @Given("a fresh disposable admin Corporate Actions draft exists")
  public void freshDisposableAdminDraft() {
    draft.start_from_observed_admin_session();
    draft.open_cleanup_preflight_list();
    draft.prove_cleanup_contract_before_save();
    draft.open_creation_surface_without_saving();
    draft.choose_observed_company("LV");
    draft.choose_observed_form("bonus");
    draft.enter_deterministic_marker(MARKER);
    draft.save_exactly_one_disposable_draft();
    draft.assert_saved_marker_and_status(MARKER, "Draft");
  }

  @When("I assign the disposable application to an available internal user")
  public void assignDisposableApplication() {
    assignmentBodyBefore = bodyText();
    clickUnique("Assign internal user");
    sleep(250);

    SelenideElement candidate = assignmentSelect();
    if (candidate != null) {
      Select select = new Select(candidate.getWrappedElement());
      for (WebElement option : select.getOptions()) {
        String label = clean(option.getText());
        String value = clean(option.getAttribute("value"));
        if (!option.isEnabled() || label.isBlank() || value.isBlank()
            || label.toLowerCase(Locale.ROOT).contains("select")) continue;
        select.selectByVisibleText(option.getText());
        selectedInternalUser = label;
        break;
      }
      if (selectedInternalUser.isBlank()) {
        throw new AssertionError("Assignment editor exposed no selectable internal user");
      }
    }

    clickUnique("Save assignment");
  }

  @Then("the disposable assignment is saved")
  public void assignmentSaved() {
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      String body = bodyText();
      boolean changed = !body.equals(assignmentBodyBefore);
      boolean selectedVisible = !selectedInternalUser.isBlank() && body.contains(selectedInternalUser);
      boolean saveGone = visibleControls("Save assignment").isEmpty();
      boolean assignmentSignal = lower(body).contains("assign") || lower(body).contains("internal user");
      if (saveGone && changed && (selectedVisible || assignmentSignal)) return;
      sleep(200);
    }
    throw new AssertionError("Assignment save did not expose a changed application state; url=" + url());
  }

  @When("I attach the harmless test fixture in the Attachments tab")
  public void attachHarmlessFixture() {
    openTab("Attachments");
    Path fixture = fixturePath();
    attachmentName = fixture.getFileName().toString();

    SelenideElement input = firstFileInput();
    if (input == null) {
      clickUnique("Attach file");
      input = awaitFileInput();
    }
    input.uploadFile(fixture.toFile());

    if (!bodyText().contains(attachmentName)) {
      List<String> commitLabels = List.of("Upload", "Add attachment", "Save attachment", "Attach");
      for (String label : commitLabels) {
        List<SelenideElement> controls = visibleControls(label);
        if (controls.size() == 1) {
          controls.get(0).click();
          break;
        }
      }
    }
  }

  @Then("the disposable attachment is visible")
  public void attachmentVisible() {
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      if (!attachmentName.isBlank() && bodyText().contains(attachmentName)) return;
      sleep(200);
    }
    throw new AssertionError("Uploaded fixture was not visible in the Attachments surface: " + attachmentName);
  }

  @When("I initiate signing and open the disposable signees surface")
  public void initiateSigningAndOpenSignees() {
    openTab("Signatures");
    clickFirstUnique(List.of("Initiate signing process", "Initiate signature"));
    sleep(300);
    List<SelenideElement> view = visibleControls("View signees");
    if (view.size() == 1) view.get(0).click();
  }

  @Then("the disposable signees surface is visible")
  public void signeesVisible() {
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      String body = lower(bodyText());
      boolean semantic = body.contains("signee") || body.contains("signer");
      boolean structured = $$("table tbody tr, [role=row], ul li").filterBy(visible).size() > 0;
      if (semantic && structured) return;
      sleep(200);
    }
    throw new AssertionError("Signing flow did not expose a populated signees/signers surface; url=" + url());
  }

  @When("I reject the disposable application with comment {string}")
  public void rejectDisposableApplication(String comment) {
    clickUnique("Reject application");
    sleep(250);

    SelenideElement commentField = rejectionCommentField();
    if (commentField == null) {
      List<SelenideElement> addComment = visibleControls("Add comment");
      if (addComment.size() == 1) {
        addComment.get(0).click();
        commentField = rejectionCommentField();
      }
    }
    if (commentField == null) {
      throw new AssertionError("Reject application did not expose a comment/reason field");
    }
    commentField.setValue(comment);
    clickFirstUnique(List.of("Confirm reject", "Reject", "Confirm"));
  }

  @Then("the disposable application status is Invalid")
  public void rejectedStatusInvalid() {
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      for (SelenideElement element : $$("body *")) {
        if (!element.isDisplayed()) continue;
        if ("invalid".equalsIgnoreCase(clean(element.getText()))) return;
      }
      sleep(200);
    }
    throw new AssertionError("Rejected disposable application did not expose exact Invalid status; url=" + url());
  }

  private static void openTab(String name) {
    if (CorporateActionsTabProbe.isActive(name)) return;
    WebElement clickable = CorporateActionsTabProbe.findClickable(name);
    if (clickable == null) throw new AssertionError("No observed " + name + " tab control was found");
    CorporateActionsTabProbe.prepare(name);
    $(clickable).scrollIntoView("{block:'center',inline:'center'}").click();
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      if (CorporateActionsTabProbe.isActive(name)) return;
      sleep(200);
    }
    throw new AssertionError(name + " tab never became active");
  }

  private static SelenideElement assignmentSelect() {
    List<SelenideElement> visibleSelects = new ArrayList<>();
    for (SelenideElement select : $$("select")) {
      if (!select.isDisplayed() || !select.isEnabled()) continue;
      visibleSelects.add(select);
      String clue = lower(String.join(" ", clean(select.getAttribute("id")), clean(select.getAttribute("name")),
        clean(select.getAttribute("aria-label")), clean(select.getAttribute("title"))));
      if (clue.contains("user") || clue.contains("assign") || clue.contains("assignee")) return select;
    }
    return visibleSelects.size() == 1 ? visibleSelects.get(0) : null;
  }

  private static SelenideElement rejectionCommentField() {
    for (SelenideElement field : $$("textarea, input:not([type=hidden]):not([type=checkbox]):not([type=radio])")) {
      if (!field.isDisplayed() || !field.isEnabled()) continue;
      String clue = lower(String.join(" ", clean(field.getAttribute("id")), clean(field.getAttribute("name")),
        clean(field.getAttribute("placeholder")), clean(field.getAttribute("aria-label"))));
      if (clue.contains("comment") || clue.contains("reason") || clue.contains("reject")) return field;
    }
    return null;
  }

  private static SelenideElement firstFileInput() {
    for (SelenideElement input : $$("input[type=file]")) {
      if (input.exists() && input.isEnabled()) return input;
    }
    return null;
  }

  private static SelenideElement awaitFileInput() {
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      SelenideElement input = firstFileInput();
      if (input != null) return input;
      sleep(100);
    }
    throw new AssertionError("Attachments surface did not expose a file input");
  }

  private static Path fixturePath() {
    URL resource = AdminMutationRepairSteps.class.getResource(FIXTURE);
    if (resource == null) throw new AssertionError("Missing harmless fixture " + FIXTURE);
    try {
      Path path = Path.of(resource.toURI());
      if (!Files.isRegularFile(path) || Files.size(path) == 0) throw new AssertionError("Harmless fixture is empty");
      return path;
    } catch (Exception error) {
      throw new AssertionError("Could not resolve harmless attachment fixture", error);
    }
  }

  private static void clickUnique(String label) {
    List<SelenideElement> controls = visibleControls(label);
    if (controls.size() != 1) {
      throw new AssertionError("Expected one visible enabled control '" + label + "', found " + controls.size());
    }
    controls.get(0).click();
  }

  private static void clickFirstUnique(List<String> labels) {
    for (String label : labels) {
      List<SelenideElement> controls = visibleControls(label);
      if (controls.size() == 1) {
        controls.get(0).click();
        return;
      }
      if (controls.size() > 1) {
        throw new AssertionError("Ambiguous control '" + label + "': " + controls.size());
      }
    }
    throw new AssertionError("None of the expected controls was visible: " + labels);
  }

  private static List<SelenideElement> visibleControls(String expected) {
    List<SelenideElement> result = new ArrayList<>();
    String wanted = clean(expected);
    for (SelenideElement control : $$("button,a,[role=button],input[type=button],input[type=submit]")) {
      if (!control.isDisplayed() || !control.isEnabled()) continue;
      String label = clean(control.getText());
      if (label.isBlank()) label = clean(control.getAttribute("value"));
      if (label.isBlank()) label = clean(control.getAttribute("aria-label"));
      if (wanted.equalsIgnoreCase(label)) result.add(control);
    }
    return result;
  }

  private static String bodyText() {
    return $("body").shouldBe(visible).getText();
  }

  private static String clean(String value) {
    return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
  }

  private static String lower(String value) {
    return clean(value).toLowerCase(Locale.ROOT);
  }
}
