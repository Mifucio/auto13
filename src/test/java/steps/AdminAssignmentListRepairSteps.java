package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.support.ui.Select;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.sleep;

/**
 * CA-33 live UI path. Assignment is a row-level CSD User select on the
 * Submitted Corporate Actions list, not a detail-page button. The mutation is
 * rolled back to the exact original option after the scenario.
 */
public final class AdminAssignmentListRepairSteps {
  private int candidateOrdinal = -1;
  private String originalValue = "";
  private String assignedValue = "";
  private String assignedLabel = "";

  @When("I assign the latest observed Submitted Bonus Issue to an available CSD user")
  public void assignLatestObservedSubmittedBonus() {
    List<SelenideElement> rows = candidateRows();
    if (rows.isEmpty()) {
      throw new AssertionError("CA-33 found no visible Submitted Bonus Issue row for AutotestLtSingleSignee");
    }

    candidateOrdinal = 0;
    SelenideElement selectElement = assignmentSelect(rows.get(candidateOrdinal));
    Select select = new Select(selectElement.getWrappedElement());
    originalValue = safe(selectElement.getValue());

    int selectedIndex = -1;
    for (int index = 0; index < select.getOptions().size(); index++) {
      var option = select.getOptions().get(index);
      String value = safe(option.getAttribute("value")).trim();
      String label = clean(option.getText());
      if (!option.isEnabled() || value.isBlank() || "null".equalsIgnoreCase(value)
          || label.toLowerCase(Locale.ROOT).contains("unassigned")) continue;
      selectedIndex = index;
      assignedValue = value;
      assignedLabel = label;
      break;
    }
    if (selectedIndex < 0) {
      throw new AssertionError("CA-33 CSD User select exposed no assignable internal user");
    }

    select.selectByIndex(selectedIndex);
    dispatchChange(selectElement);
    awaitAssignedValue(assignedValue);
    System.out.println("CA33_ASSIGNED csd_user=" + assignedLabel + " value=" + assignedValue);
  }

  @Then("the latest observed Submitted Bonus Issue retains the assigned CSD user")
  public void assignedUserRetained() {
    if (candidateOrdinal < 0 || assignedValue.isBlank()) {
      throw new AssertionError("CA-33 assertion ran without an assignment mutation");
    }
    awaitAssignedValue(assignedValue);
  }

  @After(value = "@ca33_assignment_repair", order = 1500)
  public void restoreAssignment(Scenario scenario) {
    if (candidateOrdinal < 0 || originalValue == null) return;
    try {
      List<SelenideElement> rows = candidateRows();
      if (rows.size() <= candidateOrdinal) {
        scenario.log("CA-33 rollback could not refind the original Submitted Bonus Issue row");
        return;
      }
      SelenideElement selectElement = assignmentSelect(rows.get(candidateOrdinal));
      Select select = new Select(selectElement.getWrappedElement());
      boolean restored = false;
      for (int index = 0; index < select.getOptions().size(); index++) {
        String value = safe(select.getOptions().get(index).getAttribute("value"));
        if (value.equals(originalValue)) {
          select.selectByIndex(index);
          dispatchChange(selectElement);
          restored = true;
          break;
        }
      }
      if (!restored && originalValue.isBlank()) {
        // Some browsers expose option value "null" while WebElement#getValue
        // reports an empty string. Restore the observed Unassigned option.
        for (int index = 0; index < select.getOptions().size(); index++) {
          String label = clean(select.getOptions().get(index).getText()).toLowerCase(Locale.ROOT);
          if (label.contains("unassigned")) {
            select.selectByIndex(index);
            dispatchChange(selectElement);
            restored = true;
            break;
          }
        }
      }
      if (!restored) scenario.log("CA-33 rollback did not find original CSD User option value=" + originalValue);
    } catch (Throwable failure) {
      scenario.log("CA-33 rollback warning: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
    } finally {
      candidateOrdinal = -1;
      originalValue = "";
      assignedValue = "";
      assignedLabel = "";
    }
  }

  private void awaitAssignedValue(String expected) {
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 10000);
    String last = "";
    while (System.currentTimeMillis() < deadline) {
      List<SelenideElement> rows = candidateRows();
      if (rows.size() > candidateOrdinal && candidateOrdinal >= 0) {
        SelenideElement selectElement = assignmentSelect(rows.get(candidateOrdinal));
        last = safe(selectElement.getValue());
        if (expected.equals(last)) return;
      }
      sleep(150);
    }
    throw new AssertionError("CA-33 CSD User assignment was not retained; expected=" + expected + " observed=" + last);
  }

  private static List<SelenideElement> candidateRows() {
    List<SelenideElement> result = new ArrayList<>();
    for (SelenideElement row : $$("tbody tr")) {
      if (!row.isDisplayed()) continue;
      String text = clean(row.getText()).toLowerCase(Locale.ROOT);
      if (!text.contains("bonus issue") || !text.contains("autotestltsinglesignee") || !text.contains("submitted")) continue;
      SelenideElement select = row.$("select#field_assigned_to, select.user");
      if (select.exists() && select.isDisplayed() && select.isEnabled()) result.add(row);
    }
    return result;
  }

  private static SelenideElement assignmentSelect(SelenideElement row) {
    SelenideElement select = row.$("select#field_assigned_to, select.user");
    if (!select.exists() || !select.isDisplayed() || !select.isEnabled()) {
      throw new AssertionError("CA-33 target row did not expose an enabled CSD User select");
    }
    return select;
  }

  private static void dispatchChange(SelenideElement select) {
    executeJavaScript(
      "arguments[0].dispatchEvent(new Event('input',{bubbles:true}));"
        + "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));",
      select.getWrappedElement());
  }

  private static String clean(String value) {
    return safe(value).replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}
