package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.sleep;

/** CA-33 row-level CSD User assignment with fresh DOM lookup after every rerender. */
public final class AdminAssignmentListRepairSteps {
  private static final String COMPANY = "AutotestLtSingleSignee";
  private static final String FORM = "Bonus Issue";

  private String originalValue = "";
  private String assignedValue = "";
  private String assignedLabel = "";

  @When("I assign the latest observed Submitted Bonus Issue to an available CSD user")
  public void assignLatestObservedSubmittedBonus() {
    SelenideElement selectElement = awaitCandidateSelect();
    originalValue = safe(selectElement.getValue());
    Select select = new Select(selectElement.getWrappedElement());

    int selectedIndex = -1;
    for (int index = 0; index < select.getOptions().size(); index++) {
      WebElement option = select.getOptions().get(index);
      String value = safe(option.getAttribute("value")).trim();
      String label = clean(option.getText());
      if (!option.isEnabled() || value.isBlank() || "null".equalsIgnoreCase(value)
          || label.toLowerCase(Locale.ROOT).contains("unassigned")) continue;
      selectedIndex = index;
      assignedValue = value;
      assignedLabel = label;
      break;
    }
    if (selectedIndex < 0) throw new AssertionError("CA-33 CSD User select exposed no assignable internal user");

    select.selectByIndex(selectedIndex);
    awaitAssignedValue(assignedValue);
    System.out.println("CA33_ASSIGNED csd_user=" + assignedLabel + " value=" + assignedValue);
  }

  @Then("the latest observed Submitted Bonus Issue retains the assigned CSD user")
  public void assignedUserRetained() {
    if (assignedValue.isBlank()) throw new AssertionError("CA-33 assertion ran without an assignment mutation");
    awaitAssignedValue(assignedValue);
  }

  @After(value = "@ca33_assignment_repair", order = 1500)
  public void restoreAssignment(Scenario scenario) {
    if (assignedValue.isBlank()) return;
    try {
      SelenideElement selectElement = awaitCandidateSelect();
      Select select = new Select(selectElement.getWrappedElement());
      boolean restored = false;
      for (int index = 0; index < select.getOptions().size(); index++) {
        String value = safe(select.getOptions().get(index).getAttribute("value"));
        String label = clean(select.getOptions().get(index).getText()).toLowerCase(Locale.ROOT);
        if (value.equals(originalValue) || (originalValue.isBlank() && label.contains("unassigned"))) {
          select.selectByIndex(index);
          restored = true;
          break;
        }
      }
      if (!restored) scenario.log("CA-33 rollback did not find original CSD User option value=" + originalValue);
    } catch (Throwable failure) {
      scenario.log("CA-33 rollback warning: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
    } finally {
      originalValue = "";
      assignedValue = "";
      assignedLabel = "";
    }
  }

  private static SelenideElement awaitCandidateSelect() {
    long initialDeadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 20000);
    while (System.currentTimeMillis() < initialDeadline) {
      SelenideElement candidate = candidateSelectNow();
      if (candidate != null) return candidate;
      if (visibleRowCount() > 0) break;
      sleep(150);
    }

    applyCandidateFilters();
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 20000);
    while (System.currentTimeMillis() < deadline) {
      SelenideElement candidate = candidateSelectNow();
      if (candidate != null) return candidate;
      sleep(150);
    }
    throw new AssertionError("CA-33 found no Submitted Bonus Issue row for " + COMPANY
      + "; visible_rows=" + visibleRowInventory());
  }

  private static SelenideElement candidateSelectNow() {
    WebElement raw = executeJavaScript(
      "const form=String(arguments[0]).toLowerCase(), company=String(arguments[1]).toLowerCase();"
        + "for(const row of document.querySelectorAll('tbody tr')){"
        + " if(!row.offsetParent) continue; const t=(row.innerText||'').replace(/\\s+/g,' ').toLowerCase();"
        + " if(!t.includes(form)||!t.includes(company)||!t.includes('submitted')) continue;"
        + " const s=row.querySelector('select#field_assigned_to,select.user');"
        + " if(s && s.offsetParent && !s.disabled) return s; } return null;",
      FORM, COMPANY);
    return raw == null ? null : $(raw);
  }

  private static void applyCandidateFilters() {
    SelenideElement search = $("input[type=search][name=search]");
    if (search.exists() && search.isDisplayed() && search.isEnabled()) search.setValue(COMPANY);

    SelenideElement formNames = $("#formNames");
    if (formNames.exists() && formNames.isDisplayed() && formNames.isEnabled()) {
      formNames.click();
      long deadline = System.currentTimeMillis() + 5000;
      while (System.currentTimeMillis() < deadline) {
        SelenideElement checkbox = checkboxForVisibleLabel(FORM);
        if (checkbox != null) {
          if (!checkbox.isSelected()) checkbox.click();
          break;
        }
        sleep(100);
      }
    }
    List<SelenideElement> apply = exactVisibleControls("Apply filters");
    if (apply.size() == 1) apply.get(0).click();
  }

  private static SelenideElement checkboxForVisibleLabel(String expected) {
    for (SelenideElement label : $$("label.form-check-label")) {
      if (!label.isDisplayed() || !expected.equalsIgnoreCase(clean(label.getText()))) continue;
      String forId = safe(label.getAttribute("for"));
      if (forId.isBlank()) continue;
      SelenideElement checkbox = $("#" + forId);
      if (checkbox.exists() && checkbox.isEnabled()) return checkbox;
    }
    return null;
  }

  private static void awaitAssignedValue(String expected) {
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 12000);
    String last = "";
    while (System.currentTimeMillis() < deadline) {
      SelenideElement current = candidateSelectNow();
      if (current != null) {
        last = safe(current.getValue());
        if (expected.equals(last)) return;
      }
      sleep(150);
    }
    throw new AssertionError("CA-33 assignment was not retained; expected=" + expected + " observed=" + last);
  }

  private static int visibleRowCount() {
    Number count = executeJavaScript("return [...document.querySelectorAll('tbody tr')].filter(r=>r.offsetParent!==null).length;");
    return count == null ? 0 : count.intValue();
  }

  private static String visibleRowInventory() {
    Object rows = executeJavaScript(
      "return JSON.stringify([...document.querySelectorAll('tbody tr')].filter(r=>r.offsetParent!==null)"
        + ".slice(0,10).map(r=>(r.innerText||'').replace(/\\s+/g,' ').trim()));");
    return String.valueOf(rows);
  }

  private static List<SelenideElement> exactVisibleControls(String expected) {
    List<SelenideElement> result = new ArrayList<>();
    for (SelenideElement control : $$("button,a,[role=button],input[type=button],input[type=submit]")) {
      if (!control.isDisplayed() || !control.isEnabled()) continue;
      String label = clean(control.getText());
      if (label.isBlank()) label = clean(control.getAttribute("value"));
      if (label.isBlank()) label = clean(control.getAttribute("aria-label"));
      if (expected.equalsIgnoreCase(label)) result.add(control);
    }
    return result;
  }

  private static String clean(String value) {
    return safe(value).replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
  }

  private static String safe(String value) { return value == null ? "" : value; }
}
