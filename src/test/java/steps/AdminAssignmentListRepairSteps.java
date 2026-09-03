package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

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
  private String targetRowId = "";

  @When("I assign the latest observed Submitted Bonus Issue to an available CSD user")
  public void assignLatestObservedSubmittedBonus() {
    targetRowId = awaitTargetRowIdentity();
    if (targetRowId.isBlank()) {
      throw new AssertionError("CA-33 candidate row exposed no stable identity");
    }
    AssignmentChoice choice = awaitAssignmentChoice();
    originalValue = choice.originalValue;
    assignedValue = choice.assignedValue;
    if (assignedValue.isBlank()) {
      throw new AssertionError("CA-33 CSD User select exposed no different assignable internal user");
    }
    selectAssignmentValue(assignedValue, false);
    awaitAssignedValue(assignedValue);
    NetworkBusinessWaitRepair.waitForBusinessData();
    com.codeborne.selenide.Selenide.refresh();
    awaitCandidateSelect();
    awaitAssignedValue(assignedValue);
    System.out.println("CA33_ASSIGNED persisted=true");
  }

  private String awaitTargetRowIdentity() {
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 10000);
    while (System.currentTimeMillis() < deadline) {
      try {
        SelenideElement selectElement = awaitCandidateSelect();
        Object observed = executeJavaScript(
          "const row=arguments[0].closest('tr');if(!row)return '';if(row.id)return 'id:'+String(row.id);"
            + "const link=[...row.querySelectorAll('a[href]')].find(a=>/corporate-actions|application/i.test(a.getAttribute('href')||''));"
            + "if(link)return 'href:'+String(link.getAttribute('href')||'');"
            + "const stable=[...row.querySelectorAll('td')].filter(cell=>!cell.contains(arguments[0]))"
            + ".map(cell=>(cell.innerText||'').replace(/\\s+/g,' ').trim()).join('\\u001f');"
            + "return stable?'cells:'+stable:'';",
          selectElement.getWrappedElement());
        String identity = observed == null ? "" : String.valueOf(observed).trim();
        if (!identity.isBlank()) return identity;
      } catch (StaleElementReferenceException ignored) {
        // The assignment list rerenders asynchronously. Reacquire the row rather
        // than carrying a WebElement across that render boundary.
      }
      sleep(100);
    }
    return "";
  }

  private AssignmentChoice awaitAssignmentChoice() {
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 10000);
    while (System.currentTimeMillis() < deadline) {
      try {
        SelenideElement selectElement = awaitCandidateSelect();
        Object observed = executeJavaScript(
          "const s=arguments[0],original=String(s.value||'').trim();let candidate='';"
            + "for(const option of s.options){const value=String(option.value||'').trim();"
            + "const label=String(option.textContent||'').toLowerCase();"
            + "if(option.disabled||!value||value.toLowerCase()==='null'||value===original||label.includes('unassigned'))continue;"
            + "candidate=value;break;}return [original,candidate];",
          selectElement.getWrappedElement());
        if (observed instanceof List<?> values && values.size() >= 2) {
          return new AssignmentChoice(
            safe(values.get(0) == null ? null : String.valueOf(values.get(0))),
            safe(values.get(1) == null ? null : String.valueOf(values.get(1))));
        }
      } catch (StaleElementReferenceException ignored) {
        // Reacquire both the select and its options after an Angular rerender.
      }
      sleep(100);
    }
    throw new AssertionError("CA-33 CSD User select did not remain stable long enough to inspect");
  }

  private static final class AssignmentChoice {
    final String originalValue;
    final String assignedValue;

    AssignmentChoice(String originalValue, String assignedValue) {
      this.originalValue = originalValue;
      this.assignedValue = assignedValue;
    }
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
      selectAssignmentValue(originalValue, true);
      NetworkBusinessWaitRepair.waitForBusinessData();
      com.codeborne.selenide.Selenide.refresh();
      awaitCandidateSelect();
      awaitAssignedValue(originalValue);
      originalValue = "";
      assignedValue = "";
      targetRowId = "";
    } catch (Throwable failure) {
      // Preserve the baseline and target identity so a later teardown/retry can
      // still repair the exact row instead of silently losing cleanup state.
      throw new AssertionError("CA-33 rollback failed", failure);
    }
  }

  private SelenideElement awaitCandidateSelect() {
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
    throw new AssertionError("CA-33 found no Submitted Bonus Issue row for the configured fixture"
      + "; visible_row_count=" + visibleRowCount());
  }

  private SelenideElement candidateSelectNow() {
    WebElement raw = executeJavaScript(
      "const form=String(arguments[0]).toLowerCase(), company=String(arguments[1]).toLowerCase(),target=String(arguments[2]||'');"
        + "for(const row of document.querySelectorAll('tbody tr')){"
        + " if(!row.offsetParent) continue; const t=(row.innerText||'').replace(/\\s+/g,' ').toLowerCase();"
        + " if(target){let identity='';if(row.id)identity='id:'+String(row.id);else{"
        + "const a=[...row.querySelectorAll('a[href]')].find(x=>/corporate-actions|application/i.test(x.getAttribute('href')||''));"
        + "if(a)identity='href:'+String(a.getAttribute('href')||'');else{const stable=[...row.querySelectorAll('td')]"
        + ".filter(cell=>!cell.querySelector('select#field_assigned_to,select.user'))"
        + ".map(cell=>(cell.innerText||'').replace(/\\s+/g,' ').trim()).join('\\u001f');identity=stable?'cells:'+stable:'';}}"
        + "if(identity!==target)continue;}"
        + " if(!t.includes(form)||!t.includes(company)||!t.includes('submitted')) continue;"
        + " const s=row.querySelector('select#field_assigned_to,select.user');"
        + " if(s && s.offsetParent && !s.disabled) return s; } return null;",
      FORM, COMPANY, targetRowId);
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

  private void awaitAssignedValue(String expected) {
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 12000);
    String last = "";
    while (System.currentTimeMillis() < deadline) {
      String current = candidateSelectedValueNow();
      if (current != null) last = current;
      if (expected.equals(last)) return;
      sleep(150);
    }
    throw new AssertionError("CA-33 assignment was not retained after persistence verification");
  }

  private String candidateSelectedValueNow() {
    Object value = executeJavaScript(
      "const form=String(arguments[0]).toLowerCase(), company=String(arguments[1]).toLowerCase(),target=String(arguments[2]||'');"
        + "for(const row of document.querySelectorAll('tbody tr')){"
        + " if(!row.offsetParent) continue; const t=(row.innerText||'').replace(/\\s+/g,' ').toLowerCase();"
        + " if(target){let identity='';if(row.id)identity='id:'+String(row.id);else{"
        + "const a=[...row.querySelectorAll('a[href]')].find(x=>/corporate-actions|application/i.test(x.getAttribute('href')||''));"
        + "if(a)identity='href:'+String(a.getAttribute('href')||'');else{const stable=[...row.querySelectorAll('td')]"
        + ".filter(cell=>!cell.querySelector('select#field_assigned_to,select.user'))"
        + ".map(cell=>(cell.innerText||'').replace(/\\s+/g,' ').trim()).join('\\u001f');identity=stable?'cells:'+stable:'';}}"
        + "if(identity!==target)continue;}"
        + " if(!t.includes(form)||!t.includes(company)||!t.includes('submitted')) continue;"
        + " const s=row.querySelector('select#field_assigned_to,select.user');"
        + " if(s && s.offsetParent && !s.disabled) return String(s.value||''); } return null;",
      FORM, COMPANY, targetRowId);
    return value == null ? null : String.valueOf(value);
  }

  private void selectAssignmentValue(String expected, boolean allowUnassigned) {
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 10000);
    while (System.currentTimeMillis() < deadline) {
      try {
        SelenideElement select = awaitCandidateSelect();
        Object selected = executeJavaScript(
          "const s=arguments[0],expected=String(arguments[1]||''),allowUnassigned=Boolean(arguments[2]);"
            + "for(let index=0;index<s.options.length;index++){const option=s.options[index];"
            + "const value=String(option.value||''),label=String(option.textContent||'').toLowerCase();"
            + "if(value===expected||(allowUnassigned&&!expected&&label.includes('unassigned'))){"
            + "s.selectedIndex=index;s.dispatchEvent(new Event('input',{bubbles:true}));"
            + "s.dispatchEvent(new Event('change',{bubbles:true}));s.dispatchEvent(new Event('blur',{bubbles:true}));"
            + "return true;}}return false;",
          select.getWrappedElement(), expected, allowUnassigned);
        if (Boolean.TRUE.equals(selected)) return;
      } catch (StaleElementReferenceException ignored) {
        // Reacquire the exact pinned row and retry the atomic selection.
      }
      sleep(100);
    }
    throw new AssertionError(allowUnassigned
      ? "CA-33 rollback did not find the original CSD User option"
      : "CA-33 assignment option disappeared before selection");
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
