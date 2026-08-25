package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.WebDriverRunner.url;

/**
 * Makes disposable Corporate Actions scenarios independent from execution
 * order. The wrapper waits for async form/Dokobit controls, verifies represented
 * company identity, and routes create/save through the live-runtime repair
 * helper without changing the observable customer workflow.
 */
public final class DisposableScenarioPrerequisites {
  private static final String DEFAULT_COMPANY = "AutotestLtSingleSignee";

  private final DisposableDividendSteps flow;
  private final DisposableExecutionRepairSteps repair;

  public DisposableScenarioPrerequisites(DisposableDividendSteps flow,
                                         DisposableExecutionRepairSteps repair) {
    this.flow = flow;
    this.repair = repair;
  }

  @Given("a fresh saved disposable {string} application exists")
  public void freshSavedDisposableApplication(String type) throws Exception {
    loginWithDokobitReadinessRetry();
    selectAndVerifyCompany(DEFAULT_COMPANY);
    CustomerRepairSteps.ensureCustomerEnglish();
    flow.openCorporateActions();
    repair.openCreateApplicationSafely();
    flow.chooseLastApplicationType(type);
    flow.formVisible();
    awaitSourceInstrumentControl(type);
    repair.fillAndSafelySaveDraft(type);
    flow.signDocumentVisibleStep();
    flow.persistContract();
  }

  @And("I select and verify company {string} for the disposable application")
  public void selectAndVerifyCompany(String company) {
    AssertionError last = null;
    for (int attempt = 1; attempt <= 2; attempt++) {
      try {
        String current = url();
        if (current != null && current.contains("/company-selection")) {
          CustomerRepairSteps.selectRepresentedCompanyCardOnSelectionPage(company);
        } else {
          flow.selectCompany(company);
        }
        assertOrRepairCompanyContext(company);
        return;
      } catch (AssertionError failure) {
        last = failure;
        String current = url();
        // Cached customer auth was observed expiring exactly on a company-card
        // click, bouncing to /login. Re-authenticate once and retry the same
        // company rather than treating stale session state as a business fail.
        if (attempt == 1 && current != null && current.contains("/login")) {
          loginWithDokobitReadinessRetry();
          continue;
        }
        throw failure;
      }
    }
    throw last == null ? new AssertionError("Company selection failed without an observable cause") : last;
  }

  private void loginWithDokobitReadinessRetry() {
    AssertionError last = null;
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        flow.login();
        return;
      } catch (AssertionError error) {
        last = error;
        String message = error.getMessage() == null ? "" : error.getMessage();
        if (!message.contains("Dokobit country option") || attempt == 3) throw error;
        System.out.println("DISPOSABLE_DOKOBIT_READINESS_RETRY attempt=" + attempt);
        sleep(1200);
      }
    }
    if (last != null) throw last;
  }

  private static void awaitSourceInstrumentControl(String type) {
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 20000);
    while (System.currentTimeMillis() < deadline) {
      for (SelenideElement field : $$("select[id*='security_name'], select[name*='security_name'], [role=combobox][id*='security_name']")) {
        if (field.exists() && field.isDisplayed() && field.isEnabled()) return;
      }
      sleep(100);
    }
    List<String> observed = new ArrayList<>();
    for (SelenideElement field : $$("select, [role=combobox]")) {
      if (!field.exists()) continue;
      observed.add(field.getTagName() + "#" + String.valueOf(field.getAttribute("id"))
        + " visible=" + field.isDisplayed() + " enabled=" + field.isEnabled());
    }
    throw new AssertionError("Disposable " + type + " form did not finish rendering its source instrument control; observed=" + observed);
  }

  private static void assertOrRepairCompanyContext(String company) {
    SelenideElement selected = $("#navbarRepresentedDropdown").shouldBe(visible);
    if (normalized(selected.getText()).contains(normalized(company))) return;

    selected.click();
    sleep(250);
    List<SelenideElement> matches = new ArrayList<>();
    for (SelenideElement candidate : $$("a,button,[role=menuitem],[role=button],li,div,span")) {
      if (!candidate.isDisplayed() || !candidate.isEnabled()) continue;
      String label = normalized(candidate.getText());
      if (label.equals(normalized(company))) matches.add(candidate);
    }
    if (matches.isEmpty()) {
      throw new AssertionError("Authenticated session represents a different company and the requested company was not selectable: " + company);
    }
    matches.get(matches.size() - 1).click();
    $("#navbarRepresentedDropdown").shouldHave(text(company));
  }

  private static String normalized(String value) {
    return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
  }
}
