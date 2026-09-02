package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.WebDriverRunner.getWebDriver;
import static com.codeborne.selenide.WebDriverRunner.hasWebDriverStarted;
import static com.codeborne.selenide.WebDriverRunner.url;

/**
 * Makes disposable Corporate Actions scenarios independent from execution
 * order and stale browser sessions. Application type selection is scoped to
 * the live chooser modal; draft saving keeps type-specific business branches.
 */
public final class DisposableScenarioPrerequisites {
  private static final String DEFAULT_COMPANY = "AutotestLtSingleSignee";
  private static final String ADDITIONAL_BONDS = "Additional issuance of Bonds";
  private static final Path SESSION_COOKIES = Path.of("build", "private", "customer-session.cookies");

  private final DisposableDividendSteps flow;
  private final DisposableExecutionRepairSteps repair;
  private final AdditionalBondsBothBranchRepairSteps additionalBonds;
  private final ApplicationTypeRepairSteps applicationTypes;
  private final ReliableDisposableDraftRepairSteps reliableDraft;

  public DisposableScenarioPrerequisites(DisposableDividendSteps flow,
                                         DisposableExecutionRepairSteps repair,
                                         AdditionalBondsBothBranchRepairSteps additionalBonds,
                                         ApplicationTypeRepairSteps applicationTypes,
                                         ReliableDisposableDraftRepairSteps reliableDraft) {
    this.flow = flow;
    this.repair = repair;
    this.additionalBonds = additionalBonds;
    this.applicationTypes = applicationTypes;
    this.reliableDraft = reliableDraft;
  }

  @Given("a fresh saved disposable {string} application exists")
  public void freshSavedDisposableApplication(String type) throws Exception {
    prepareAuthenticatedCompany(DEFAULT_COMPANY);
    CustomerRepairSteps.ensureCustomerEnglish();
    flow.openCorporateActions();
    repair.openCreateApplicationSafely();
    applicationTypes.chooseObservedApplicationType(type);
    flow.formVisible();
    awaitSourceInstrumentControl(type);
    if (ADDITIONAL_BONDS.equalsIgnoreCase(type)) {
      additionalBonds.fillAndSaveBothBranch();
    } else {
      reliableDraft.fillAndReliablySaveDraft(type);
    }
    flow.signDocumentVisibleStep();
    flow.persistContract();
  }

  /** Re-establishes the cheap authenticated customer context without creating data. */
  void prepareReusableCustomerContext() {
    prepareAuthenticatedCompany(DEFAULT_COMPANY);
    CustomerRepairSteps.ensureCustomerEnglish();
  }

  @And("I select and verify company {string} for the disposable application")
  public void selectAndVerifyCompany(String company) {
    String current = url();
    if (current != null && current.contains("/company-selection")) {
      CustomerRepairSteps.selectRepresentedCompanyCardOnSelectionPage(company);
    } else {
      flow.selectCompany(company);
    }
    assertOrRepairCompanyContext(company);
  }

  private void prepareAuthenticatedCompany(String company) {
    AssertionError last = null;
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        loginWithDokobitReadinessRetry();
        selectAndVerifyCompany(company);
        CustomerRepairSteps.ensureCustomerEnglish();
        return;
      } catch (AssertionError error) {
        last = error;
        String current = url();
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        boolean staleSession = (current != null && current.contains("/login"))
          || message.contains("did not leave /company-selection")
          || message.contains("customer authentication did not render")
          || message.contains("not authorized")
          || message.contains("not authorised");
        if (!staleSession || attempt == 3) throw error;
        System.out.println("DISPOSABLE_STALE_SESSION_RETRY attempt=" + attempt + " url=" + current);
        clearCachedCustomerAuthentication();
        sleep(800);
      }
    }
    if (last != null) throw last;
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
        System.out.println("DISPOSABLE_LOGIN_READINESS_RETRY attempt=" + attempt);
        sleep(1200);
      }
    }
    if (last != null) throw last;
  }

  private static void clearCachedCustomerAuthentication() {
    try { Files.deleteIfExists(SESSION_COOKIES); } catch (Exception ignored) { }
    if (!hasWebDriverStarted()) return;
    try { getWebDriver().manage().deleteAllCookies(); } catch (Throwable ignored) { }
    try { executeJavaScript("try{localStorage.clear()}catch(e){} try{sessionStorage.clear()}catch(e){}"); }
    catch (Throwable ignored) { }
  }

  private static void awaitSourceInstrumentControl(String type) {
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 20000);
    while (System.currentTimeMillis() < deadline) {
      for (SelenideElement candidate : $$(
          "select[id*='security_name'], select[name*='security_name'], [role=combobox][id*='security_name']")) {
        if (candidate.exists() && candidate.isDisplayed() && candidate.isEnabled()) return;
      }
      sleep(100);
    }

    List<String> inventory = new ArrayList<>();
    for (SelenideElement candidate : $$("select, [role=combobox]")) {
      if (!candidate.exists()) continue;
      inventory.add(candidate.getTagName() + "#" + candidate.getAttribute("id")
        + ":displayed=" + candidate.isDisplayed() + ":enabled=" + candidate.isEnabled());
    }
    throw new AssertionError("Disposable " + type
      + " form did not expose a ready Source instrument control; observed=" + inventory);
  }

  /**
   * Verifies the active represented-company context and repairs only the
   * customer SPA selection when the restored authenticated shell has no active
   * company. This deliberately leaves cookies, XSRF state, and browser storage
   * untouched so callers can invoke it immediately before a protected API
   * action.
   */
  public static boolean ensureRepresentedCompanyReady(String company) {
    String current = url();
    if (current == null || current.contains("/login")) {
      throw new AssertionError("Cannot establish represented company while customer session is on login; url=" + current);
    }
    if (current.contains("/company-selection")) {
      CustomerRepairSteps.selectRepresentedCompanyCardOnSelectionPage(company);
    }

    SelenideElement selected = $("#navbarRepresentedDropdown").shouldBe(visible);
    if (normalized(selected.getText()).contains(normalized(company))) return true;

    selected.click();
    sleep(250);
    List<SelenideElement> matches = new ArrayList<>();
    for (SelenideElement candidate : $$("a,button,[role=menuitem],[role=button],li,div,span")) {
      if (!candidate.isDisplayed() || !candidate.isEnabled()) continue;
      String label = normalized(candidate.getText());
      if (label.equals(normalized(company))) matches.add(candidate);
    }
    if (matches.size() != 1) {
      throw new AssertionError("Authenticated session represents a different company and the requested company did not expose exactly one selectable option: "
        + company + "; found=" + matches.size());
    }
    matches.get(0).click();
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      SelenideElement refreshed = $("#navbarRepresentedDropdown");
      if (refreshed.isDisplayed() && normalized(refreshed.getText()).contains(normalized(company))) return true;
      sleep(100);
    }
    throw new AssertionError("Represented-company SPA selection did not establish the requested company: " + company
      + "; url=" + url());
  }

  private static void assertOrRepairCompanyContext(String company) {
    ensureRepresentedCompanyReady(company);
  }

  private static String normalized(String value) {
    return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
  }
}
