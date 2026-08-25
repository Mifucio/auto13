package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.When;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.support.ui.Select;

import java.util.List;
import java.util.Locale;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.sleep;

/** Keeps Additional issuance of Bonds on the observed Both paid-up branch. */
public final class AdditionalBondsBothBranchRepairSteps {
  private static final String TYPE = "Additional issuance of Bonds";

  private final DisposableDividendSteps flow;
  private final DisposableExecutionRepairSteps repair;

  public AdditionalBondsBothBranchRepairSteps(DisposableDividendSteps flow,
                                               DisposableExecutionRepairSteps repair) {
    this.flow = flow;
    this.repair = repair;
  }

  @When("I fill and safely save the disposable Additional issuance of Bonds form on the Both paid-up branch")
  public void fillAndSaveBothBranch() throws Exception {
    try {
      flow.fillDisposableApplicationAndSaveDraft(TYPE);
      return;
    } catch (Throwable failure) {
      if (!isRepairableAibFailure(failure)) rethrow(failure);
      System.out.println("AIB_BOTH_REPAIR root=" + rootCause(failure).getClass().getSimpleName());
    }

    setObservedMinimumAdditionalNominalValue();
    resolveObservedBondholderRow();
    repair.safelySavePreparedDraft();
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
      long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 10000);
      while (System.currentTimeMillis() < deadline) {
        if (hasSelectableOption(account) && hasSelectableOption(name)) break;
        sleep(150);
      }
    }

    if (!hasSelectableOption(account) || !hasSelectableOption(name)) {
      throw new AssertionError("AIB bondholder lookup returned no account/name options for code " + holderCode);
    }

    selectFirstNonEmpty(account);
    selectFirstNonEmpty(name);

    String accountLabel = new Select(account.getWrappedElement()).getFirstSelectedOption().getText();
    boolean distributionAccount = safe(accountLabel).toLowerCase(Locale.ROOT).contains("distribution account");
    SelenideElement nominal = distributionAccount ? $("#aib_nominal_value_unpaid") : $("#aib_nominal_value_paid");
    String requiredAmount = numericValue(nominal.exists() ? nominal.getValue() : "");
    if (requiredAmount.isBlank()) requiredAmount = distributionAccount ? "0" : "2000";
    setAndDispatch(amount, requiredAmount);
    System.out.println("AIB_DISTRIBUTION_AMOUNT account=" + accountLabel + " amount=" + requiredAmount);
  }

  private static boolean hasSelectableOption(SelenideElement field) {
    if (!field.exists() || !"select".equalsIgnoreCase(field.getTagName())) return false;
    for (org.openqa.selenium.WebElement option : new Select(field.getWrappedElement()).getOptions()) {
      String value = safe(option.getAttribute("value")).trim();
      String label = safe(option.getText()).trim();
      if (option.isEnabled() && !value.isBlank() && !"null".equalsIgnoreCase(value)
          && !label.toLowerCase(Locale.ROOT).contains("select")) return true;
    }
    return false;
  }

  private static void selectFirstNonEmpty(SelenideElement field) {
    Select select = new Select(field.getWrappedElement());
    List<org.openqa.selenium.WebElement> options = select.getOptions();
    for (int index = 0; index < options.size(); index++) {
      org.openqa.selenium.WebElement option = options.get(index);
      String value = safe(option.getAttribute("value")).trim();
      String label = safe(option.getText()).trim();
      if (!option.isEnabled() || value.isBlank() || "null".equalsIgnoreCase(value)
          || label.toLowerCase(Locale.ROOT).contains("select")) continue;
      select.selectByIndex(index);
      return;
    }
    throw new AssertionError("AIB native select exposed no non-empty option: " + safe(field.getAttribute("id")));
  }

  private static void setAndDispatch(SelenideElement field, String value) {
    field.setValue(value);
    executeJavaScript(
      "arguments[0].dispatchEvent(new Event('input',{bubbles:true}));"
        + "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));"
        + "arguments[0].dispatchEvent(new Event('blur',{bubbles:true}));",
      field.getWrappedElement());
    sleep(150);
  }

  private static String numericValue(String raw) {
    String value = safe(raw).replace("\u00a0", "").replace(" ", "").replace("'", "");
    if (value.contains(",") && !value.contains(".")) value = value.replace(',', '.');
    else value = value.replace(",", "");
    return value.matches("-?[0-9]+(?:\\.[0-9]+)?") ? value : "";
  }

  private static boolean isRepairableAibFailure(Throwable failure) {
    Throwable root = rootCause(failure);
    String message = safe(root.getMessage()).toLowerCase(Locale.ROOT);
    return root instanceof ElementClickInterceptedException
      || root.getClass().getSimpleName().contains("ElementClickIntercepted")
      || message.contains("click intercepted")
      || message.contains("save as draft did not produce");
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
