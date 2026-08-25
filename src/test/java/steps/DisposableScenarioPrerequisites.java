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
    String current = url();
    if (current != null && current.contains("/company-selection")) {
      // The company-selection heading is localized in the Mobile-ID account
      // (EE in the captured run), so select by the observed card itself rather
      // than requiring the English "Choose who you represent" landmark.
      CustomerRepairSteps.selectRepresentedCompanyCardOnSelectionPage(company);
    } else {
      flow.selectCompany(company);
    }
    assertOrRepairCompanyContext(company);
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
