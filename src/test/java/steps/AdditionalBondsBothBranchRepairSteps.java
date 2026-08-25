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

/**
 * Keeps the Additional issuance of Bonds disposable scenario on the originally
 * observed "Both" paid-up branch. The captured failure was not caused by the
 * branch itself: the generated flow tried to treat an empty native select as a
 * custom dropdown and clicked an unrelated global <li>. Resolve the row through
 * its observed lookup button instead of changing the business choice to "Yes".
 */
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
      if (!isCapturedNativeSelectInterception(failure)) rethrow(failure);
      System.out.println("AIB_BOTH_REPAIR resolving observed bondholder row after native-select interception");
    }

    resolveObservedBondholderRow();
    repair.safelySavePreparedDraft();
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
    if (holderCode.isBlank()) {
      throw new AssertionError("AIB Both branch holder code was empty before the observed lookup");
    }

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
      throw new AssertionError("AIB bondholder lookup returned no selectable account/name options for observed code "
        + holderCode);
    }

    selectFirstNonEmpty(account);
    selectFirstNonEmpty(name);
    if (safe(amount.getValue()).trim().isBlank()) amount.setValue("1");
  }

  private static boolean hasSelectableOption(SelenideElement field) {
    if (!field.exists() || !"select".equalsIgnoreCase(field.getTagName())) return false;
    Select select = new Select(field.getWrappedElement());
    for (org.openqa.selenium.WebElement option : select.getOptions()) {
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
      executeJavaScript(
        "arguments[0].dispatchEvent(new Event('input',{bubbles:true}));"
          + "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));",
        field.getWrappedElement());
      return;
    }
    throw new AssertionError("AIB observed native select exposed no selectable non-empty option: "
      + safe(field.getAttribute("id")));
  }

  private static boolean isCapturedNativeSelectInterception(Throwable failure) {
    Throwable root = rootCause(failure);
    String message = safe(root.getMessage()).toLowerCase(Locale.ROOT);
    return root instanceof ElementClickInterceptedException
      || root.getClass().getSimpleName().contains("ElementClickIntercepted")
      || message.contains("click intercepted");
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

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}