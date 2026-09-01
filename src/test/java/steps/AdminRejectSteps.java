package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.List;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.WebDriverRunner.url;

/** CA-40 admin-only rejection against the observed disposable Submitted Bonus Issue rows. */
public final class AdminRejectSteps {
  private final AdminSteps admin;
  private final AdminMutationRepairSteps mutations;

  public AdminRejectSteps(AdminSteps admin, AdminMutationRepairSteps mutations) {
    this.admin = admin;
    this.mutations = mutations;
  }

  @When("I open the observed disposable Submitted Bonus Issue application not the newest")
  public void openDisposableSubmittedBonusNotNewest() {
    admin.i_filter_the_observed_corporate_actions_list_by_form("Bonus Issue");
    List<SelenideElement> rows = new ArrayList<>();
    long deadline = System.currentTimeMillis() + 20000;
    while (System.currentTimeMillis() < deadline) {
      rows.clear();
      for (SelenideElement row : $$("tbody tr")) {
        if (row.isDisplayed() && row.getText().toLowerCase().contains("submitted")
            && row.getText().toLowerCase().contains("autotestltsinglesignee")) rows.add(row);
      }
      if (rows.size() >= 2) break;
      sleep(200);
    }
    if (rows.size() < 2) throw new AssertionError("Expected at least 2 disposable Submitted Bonus Issue rows for AutotestLtSingleSignee, found " + rows.size());
    SelenideElement target = rows.get(1);
    SelenideElement formCell = visibleCellContaining(target, "Bonus Issue");
    formCell.scrollIntoView(true).click();
    long routeDeadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < routeDeadline) {
      String current = url();
      if (current != null && current.contains("/corporate-actions/application-form/")) {
        $("body").shouldBe(visible);
        return;
      }
      sleep(100);
    }
    throw new AssertionError("Disposable Submitted Bonus Issue row click did not open an application detail; url=" + url());
  }

  @When("I reject the observed application with comment {string}")
  public void rejectObservedApplication(String comment) {
    List<SelenideElement> triggers = new ArrayList<>();
    for (String label : List.of("Reject application", "Reject")) triggers.addAll(visibleControls(label));
    if (triggers.size() != 1) throw new AssertionError("Expected one visible enabled Reject control, found " + triggers.size());
    triggers.get(0).click();
    sleep(250);
    SelenideElement commentField = rejectionCommentField();
    if (commentField == null) {
      List<SelenideElement> addComment = visibleControls("Add comment");
      if (addComment.size() == 1) {
        addComment.get(0).click();
        commentField = rejectionCommentField();
      }
    }
    if (commentField == null) throw new AssertionError("Reject application did not expose a comment or reason field");
    commentField.setValue(comment);
    for (String label : List.of("Reject Application", "Confirm reject", "Reject", "Confirm")) {
      List<SelenideElement> buttons = visibleControls(label);
      if (buttons.isEmpty()) continue;
      buttons.get(buttons.size() - 1).click();
      return;
    }
    throw new AssertionError("Reject confirmation control not found");
  }

  private static List<SelenideElement> visibleControls(String expected) {
    List<SelenideElement> result = new ArrayList<>();
    for (SelenideElement control : $$("button,a,[role=button],input[type=button],input[type=submit]")) {
      try {
        if (!control.isDisplayed() || !control.isEnabled()) continue;
        String label = control.getText();
        if (label.isBlank()) label = control.getAttribute("value");
        if (label.isBlank()) label = control.getAttribute("aria-label");
        if (expected.equalsIgnoreCase(label)) result.add(control);
      } catch (Throwable ignored) { }
    }
    return result;
  }

  private static SelenideElement rejectionCommentField() {
    for (SelenideElement field : $$("textarea,input:not([type=hidden]):not([type=checkbox]):not([type=radio])")) {
      try {
        if (!field.isDisplayed() || !field.isEnabled()) continue;
        String clue = "" + field.getAttribute("id") + " " + field.getAttribute("name")
          + " " + field.getAttribute("placeholder") + " " + field.getAttribute("aria-label");
        if (clue.toLowerCase().contains("comment") || clue.toLowerCase().contains("reason")
            || clue.toLowerCase().contains("reject")) return field;
      } catch (Throwable ignored) { }
    }
    return null;
  }

  @Then("the observed application status is Invalid")
  public void observedApplicationInvalid() {
    mutations.rejectedStatusInvalid();
  }

  private static SelenideElement visibleCellContaining(SelenideElement row, String value) {
    String wanted = value == null ? "" : value.toLowerCase();
    for (SelenideElement cell : row.$$("td")) {
      if (cell.isDisplayed() && cell.getText().toLowerCase().contains(wanted)) return cell;
    }
    throw new AssertionError("Row did not expose a visible cell containing " + value + " row=" + row.getText());
  }
}
