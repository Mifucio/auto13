package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.When;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.support.ui.Select;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.WebDriverRunner.url;

/** Keeps Additional issuance of Bonds on the observed Both paid-up branch. */
public final class AdditionalBondsBothBranchRepairSteps {
  private static final String TYPE = "Additional issuance of Bonds";

  private final DisposableDividendSteps flow;

  public AdditionalBondsBothBranchRepairSteps(DisposableDividendSteps flow,
                                               DisposableExecutionRepairSteps repair) {
    this.flow = flow;
  }

  @When("I fill and safely save the disposable Additional issuance of Bonds form on the Both paid-up branch")
  public void fillAndSaveBothBranch() throws Exception {
    // The first selectable instrument in the live list is an equity and can
    // expose unpaid nominal requiring a special Distribution Account. For an
    // Additional issuance of Bonds scenario, prefer the observed bond option.
    selectObservedBondSourceInstrument();

    Throwable initialFailure = null;
    long previousTimeout = Configuration.timeout;
    try {
      // Never let one stale/click-intercepted generated helper consume 70 sec.
      Configuration.timeout = Math.min(previousTimeout, 12000);
      flow.fillDisposableApplicationAndSaveDraft(TYPE);
      return;
    } catch (Throwable failure) {
      initialFailure = failure;
      if (!isRepairableAibFailure(failure)) rethrow(failure);
      System.out.println("AIB_BOTH_REPAIR root=" + rootCause(failure).getClass().getSimpleName());
    } finally {
      Configuration.timeout = previousTimeout;
    }

    setObservedMinimumAdditionalNominalValue();
    resolveObservedBondholderRow();
    fastSaveDraft(initialFailure);
  }

  private static void selectObservedBondSourceInstrument() {
    SelenideElement source = $("#aib_security_name");
    if (!source.exists() || !source.isDisplayed() || !source.isEnabled()) return;

    Select select = new Select(source.getWrappedElement());
    List<org.openqa.selenium.WebElement> options = select.getOptions();
    int target = -1;
    for (int index = 0; index < options.size(); index++) {
      var option = options.get(index);
      String value = safe(option.getAttribute("value")).trim();
      String label = safe(option.getText()).trim();
      if (!option.isEnabled() || value.isBlank() || "null".equalsIgnoreCase(value)) continue;
      if (label.toLowerCase(Locale.ROOT).contains("bond")) { target = index; break; }
    }
    if (target < 0) return;

    String current = safe(select.getFirstSelectedOption().getText()).toLowerCase(Locale.ROOT);
    if (!current.contains("bond")) {
      select.selectByIndex(target);
      dispatch(source);
      long deadline = System.currentTimeMillis() + 6000;
      while (System.currentTimeMillis() < deadline) {
        String paid = numericValue(safe($("#aib_nominal_value_paid").getValue()));
        String unpaid = numericValue(safe($("#aib_nominal_value_unpaid").getValue()));
        if (!paid.isBlank() || !unpaid.isBlank()) break;
        sleep(100);
      }
    }
    System.out.println("AIB_SOURCE_INSTRUMENT " + safe(new Select(source.getWrappedElement()).getFirstSelectedOption().getText()));
  }

  private static void setObservedMinimumAdditionalNominalValue() {
    SelenideElement field = $("#aib_additional_nominal_value");
    if (!field.exists() || !field.isDisplayed() || !field.isEnabled()) {
      throw new AssertionError("AIB Both branch did not expose #aib_additional_nominal_value");
    }
    setAndDispatch(field, "2000");
    System.out.println("AIB_ADDITIONAL_NOMINAL_VALUE 2000");
  }

  private static void resolveObservedBondholderRow() {
    SelenideElement both = $("input[type=radio][name='aib_paid_up'][value='2']");
    if (!both.exists() || !both.isSelected()) {
      throw new AssertionError("AIB repair expected the observed Both paid-up branch to remain selected");
    }

    SelenideElement row = $("#aib_bondholders_table_row_0");
    SelenideElement code = $("#aib_bht_code_0");
    SelenideElement account = $("#aib_bht_account_0");
    SelenideElement name = $("#aib_bht_name_0");
    SelenideElement amount = $("#aib_bht_amount_of_bonds_issued_0");
    if (!row.exists() || !code.exists() || !account.exists() || !name.exists() || !amount.exists()) {
      throw new AssertionError("AIB Both branch did not expose the observed bondholder row controls");
    }

    String holderCode = safe(code.getValue()).trim();
    if (holderCode.isBlank()) throw new AssertionError("AIB Both branch holder code was empty before lookup");

    if (!hasSelectableOption(account) || !hasSelectableOption(name)) {
      SelenideElement search = row.$("button.button-search");
      if (!search.exists() || !search.isDisplayed() || !search.isEnabled()) {
        throw new AssertionError("AIB Both branch did not expose the observed bondholder lookup button");
      }
      executeJavaScript("arguments[0].scrollIntoView({block:'center',inline:'center'}); arguments[0].click();",
        search.getWrappedElement());
      long deadline = System.currentTimeMillis() + 8000;
      while (System.currentTimeMillis() < deadline) {
        if (hasSelectableOption(account) && hasSelectableOption(name)) break;
        sleep(100);
      }
    }

    if (!hasSelectableOption(account) || !hasSelectableOption(name)) {
      throw new AssertionError("AIB bondholder lookup returned no account/name options for code " + holderCode);
    }

    double paid = numericDouble(safe($("#aib_nominal_value_paid").getValue()));
    double unpaid = numericDouble(safe($("#aib_nominal_value_unpaid").getValue()));
    Select accountSelect = new Select(account.getWrappedElement());

    boolean needDistribution = unpaid > 0.0001;
    int accountIndex = needDistribution ? optionIndexContaining(accountSelect, "distribution account")
      : firstNonEmptyOptionIndex(accountSelect);
    if (accountIndex < 0) {
      throw new AssertionError("AIB requires " + (needDistribution ? "Distribution Account" : "an account")
        + " for paid=" + paid + " unpaid=" + unpaid + "; options=" + optionInventory(accountSelect));
    }
    accountSelect.selectByIndex(accountIndex);
    dispatch(account);
    selectFirstNonEmpty(name);

    String accountLabel = safe(accountSelect.getFirstSelectedOption().getText());
    double required = needDistribution ? unpaid : paid;
    if (required <= 0.0001 && !needDistribution) {
      // A fully-unpaid source instrument with no Distribution Account is not a
      // valid deterministic fixture for this UI scenario. Fail now, not after
      // seven 70-second save retries.
      throw new AssertionError("AIB selected source has no paid nominal for non-distribution account; paid="
        + paid + " unpaid=" + unpaid + "; account=" + accountLabel);
    }
    setAndDispatch(amount, decimal(required));
    System.out.println("AIB_DISTRIBUTION_AMOUNT account=" + accountLabel + " amount=" + decimal(required)
      + " paid=" + paid + " unpaid=" + unpaid);
  }

  private static void fastSaveDraft(Throwable initialFailure) {
    for (int attempt = 1; attempt <= 2; attempt++) {
      clickStickySaveDraft();
      long deadline = System.currentTimeMillis() + 10000;
      while (System.currentTimeMillis() < deadline) {
        if (savedDetailVisible()) return;
        sleep(100);
      }

      String validation = visibleValidationMessages();
      if (!validation.isBlank()) {
        throw new AssertionError("AIB Save as Draft rejected by visible validation: " + validation, initialFailure);
      }
      System.out.println("AIB_SAVE_RETRY attempt=" + attempt + " url=" + url());
    }
    throw new AssertionError("AIB Save as Draft did not reach an application detail within 20s; url=" + url()
      + "; validation=" + visibleValidationMessages(), initialFailure);
  }

  private static void clickStickySaveDraft() {
    Number clicked = executeJavaScript(
      "const labels=['save as draft','save as draft'];"
        + "const all=[...document.querySelectorAll('#editingNavbar button,#editingNavbar a,#editingNavbar [role=button]')];"
        + "const c=all.find(e=>e.offsetParent!==null&&!e.disabled&&String(e.innerText||e.value||'').replace(/\\s+/g,' ').trim().toLowerCase()==='save as draft');"
        + "if(c){c.click();return 1;} return 0;");
    if (clicked == null || clicked.intValue() != 1) {
      throw new AssertionError("AIB editing navbar exposed no enabled Save as Draft control");
    }
  }

  private static boolean savedDetailVisible() {
    try {
      String current = url();
      if (current != null && current.matches(".*/corporate-actions/application-form/\\d+(?:[/?#].*)?")) return true;
      String body = $("body").getText();
      return body != null && body.contains("Sign Document") && body.contains(TYPE);
    } catch (Throwable ignored) { return false; }
  }

  private static String visibleValidationMessages() {
    Object value = executeJavaScript(
      "const sel='.invalid-feedback,.alert-danger,.text-danger,[role=alert]';"
        + "return [...document.querySelectorAll(sel)].filter(e=>e.offsetParent!==null)"
        + ".map(e=>String(e.innerText||'').replace(/\\s+/g,' ').trim()).filter(Boolean).join(' | ');");
    return value == null ? "" : safe(value.toString()).trim();
  }

  private static boolean hasSelectableOption(SelenideElement field) {
    try {
      if (!field.exists() || !"select".equalsIgnoreCase(field.getTagName())) return false;
      return firstNonEmptyOptionIndex(new Select(field.getWrappedElement())) >= 0;
    } catch (Throwable ignored) { return false; }
  }

  private static int firstNonEmptyOptionIndex(Select select) {
    List<org.openqa.selenium.WebElement> options = select.getOptions();
    for (int index = 0; index < options.size(); index++) {
      var option = options.get(index);
      String value = safe(option.getAttribute("value")).trim();
      String label = safe(option.getText()).trim();
      if (option.isEnabled() && !value.isBlank() && !"null".equalsIgnoreCase(value)
          && !label.toLowerCase(Locale.ROOT).contains("select")) return index;
    }
    return -1;
  }

  private static int optionIndexContaining(Select select, String needle) {
    String wanted = needle.toLowerCase(Locale.ROOT);
    List<org.openqa.selenium.WebElement> options = select.getOptions();
    for (int index = 0; index < options.size(); index++) {
      var option = options.get(index);
      String value = safe(option.getAttribute("value")).trim();
      String label = safe(option.getText()).trim().toLowerCase(Locale.ROOT);
      if (option.isEnabled() && !value.isBlank() && !"null".equalsIgnoreCase(value) && label.contains(wanted)) return index;
    }
    return -1;
  }

  private static String optionInventory(Select select) {
    List<String> result = new ArrayList<>();
    for (var option : select.getOptions()) result.add(safe(option.getText()).trim());
    return result.toString();
  }

  private static void selectFirstNonEmpty(SelenideElement field) {
    Select select = new Select(field.getWrappedElement());
    int index = firstNonEmptyOptionIndex(select);
    if (index < 0) throw new AssertionError("AIB native select exposed no non-empty option: " + safe(field.getAttribute("id")));
    select.selectByIndex(index);
    dispatch(field);
  }

  private static void setAndDispatch(SelenideElement field, String value) {
    field.setValue(value);
    dispatch(field);
    sleep(100);
  }

  private static void dispatch(SelenideElement field) {
    executeJavaScript(
      "arguments[0].dispatchEvent(new Event('input',{bubbles:true}));"
        + "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));"
        + "arguments[0].dispatchEvent(new Event('blur',{bubbles:true}));",
      field.getWrappedElement());
  }

  private static String numericValue(String raw) {
    String value = safe(raw).replace("\u00a0", "").replace(" ", "").replace("'", "");
    if (value.contains(",") && !value.contains(".")) value = value.replace(',', '.');
    else value = value.replace(",", "");
    return value.matches("-?[0-9]+(?:\\.[0-9]+)?") ? value : "";
  }

  private static double numericDouble(String raw) {
    String value = numericValue(raw);
    try { return value.isBlank() ? 0.0 : Double.parseDouble(value); }
    catch (NumberFormatException ignored) { return 0.0; }
  }

  private static String decimal(double value) {
    if (Math.rint(value) == value) return Long.toString((long) value);
    return Double.toString(value);
  }

  private static boolean isRepairableAibFailure(Throwable failure) {
    Throwable root = rootCause(failure);
    String message = safe(root.getMessage()).toLowerCase(Locale.ROOT);
    return root instanceof ElementClickInterceptedException
      || root instanceof StaleElementReferenceException
      || root.getClass().getSimpleName().contains("ElementClickIntercepted")
      || root.getClass().getSimpleName().contains("StaleElement")
      || message.contains("click intercepted")
      || message.contains("save as draft did not produce")
      || message.contains("repaired save as draft");
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable root = failure;
    while (root != null && root.getCause() != null && root.getCause() != root) root = root.getCause();
    return root == null ? failure : root;
  }

  private static void rethrow(Throwable failure) throws Exception {
    if (failure instanceof Exception exception) throw exception;
    if (failure instanceof Error error) throw error;
    throw new AssertionError("Unexpected AIB disposable failure", failure);
  }

  private static String safe(String value) { return value == null ? "" : value; }
}
