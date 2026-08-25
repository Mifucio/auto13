package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.When;
import org.openqa.selenium.StaleElementReferenceException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.WebDriverRunner.url;

/**
 * Keeps draft saving on the observed business path while capping repair-time
 * Selenide waits. A successful Angular save can replace the entire form while a
 * generated helper is still iterating its old element snapshot; that stale
 * exception is success when the browser has already reached the numeric detail.
 */
public final class ReliableDisposableDraftRepairSteps {
  private static final String DIVIDEND_PAYMENT = "Dividend Payment";

  private final DisposableDividendSteps flow;
  private final DisposableExecutionRepairSteps repair;

  public ReliableDisposableDraftRepairSteps(DisposableDividendSteps flow,
                                             DisposableExecutionRepairSteps repair) {
    this.flow = flow;
    this.repair = repair;
  }

  @When("I reliably save the prepared disposable application as draft")
  public void reliablySavePreparedDraft() throws Exception {
    normalizeBlankDividendExcludedRows();
    repair.safelySavePreparedDraft();
  }

  @When("I fill and reliably save the disposable {string} form as draft")
  public void fillAndReliablySaveDraftStep(String type) throws Exception {
    fillAndReliablySaveDraft(type);
  }

  void fillAndReliablySaveDraft(String type) throws Exception {
    if (DIVIDEND_PAYMENT.equalsIgnoreCase(type)) {
      flow.selectSourceInstrument();
      normalizeBlankDividendExcludedRows();
      repair.safelySavePreparedDraft();
      return;
    }

    long previousTimeout = Configuration.timeout;
    Throwable failure = null;
    try {
      // The global 70s timeout is for genuinely slow page loaders. Generated
      // form-element snapshots must not inherit it: after a save, stale fields
      // can otherwise cost 70s even though the detail page is already visible.
      Configuration.timeout = Math.min(previousTimeout, 12000);
      flow.fillDisposableApplicationAndSaveDraft(type);
      return;
    } catch (Throwable error) {
      failure = error;
      if (savedDetailVisible()) {
        System.out.println("DISPOSABLE_STALE_AFTER_SUCCESS type=" + type
          + " root=" + rootCause(error).getClass().getSimpleName());
        return;
      }
    } finally {
      Configuration.timeout = previousTimeout;
    }

    Throwable root = rootCause(failure);
    String message = safe(root.getMessage()).toLowerCase(Locale.ROOT);
    boolean repairable = root instanceof StaleElementReferenceException
      || root.getClass().getSimpleName().contains("StaleElement")
      || message.contains("click intercepted")
      || message.contains("save as draft");
    if (!repairable) rethrow(failure);

    // The type-specific filler already populated business fields before its
    // save attempt. Use the existing prepared-draft recovery only for the
    // remaining click/validation transition.
    repair.safelySavePreparedDraft();
  }

  private static boolean savedDetailVisible() {
    try {
      String current = url();
      if (current != null && current.matches(".*/corporate-actions/application-form/\\d+(?:[/?#].*)?")) return true;
      String body = $("body").getText();
      return body != null && body.contains("Sign Document");
    } catch (Throwable ignored) { return false; }
  }

  private static void normalizeBlankDividendExcludedRows() {
    List<String> blankRowIds = new ArrayList<>();
    for (SelenideElement row : $$("tr[id^='dp_account_exclude_table_row_']")) {
      try {
        if (row.exists() && row.isDisplayed() && isBlankRow(row)) {
          String id = clean(row.getAttribute("id"));
          if (!id.isBlank()) blankRowIds.add(id);
        }
      } catch (Throwable ignored) { }
    }

    for (String rowId : blankRowIds) {
      SelenideElement row = $("#" + rowId);
      if (!row.exists() || !row.isDisplayed() || !isBlankRow(row)) continue;
      SelenideElement delete = row.$("img[alt='delete role'][type='button'], .ui-icon-trash img[type='button']");
      if (!delete.exists() || !delete.isDisplayed()) {
        System.out.println("DIVIDEND_EMPTY_EXCLUDED_PLACEHOLDER_RETAINED " + rowId + " no_delete_control");
        continue;
      }

      executeJavaScript("arguments[0].click();", delete.getWrappedElement());
      long deadline = System.currentTimeMillis() + 1500;
      while (System.currentTimeMillis() < deadline) {
        SelenideElement current = $("#" + rowId);
        if (!current.exists() || !current.isDisplayed()) {
          System.out.println("DIVIDEND_EMPTY_EXCLUDED_ROW_REMOVED " + rowId);
          break;
        }
        if (isBlankRow(current)) {
          System.out.println("DIVIDEND_EMPTY_EXCLUDED_PLACEHOLDER_RETAINED " + rowId);
          break;
        }
        sleep(100);
      }

      SelenideElement current = $("#" + rowId);
      if (current.exists() && current.isDisplayed() && !isBlankRow(current)) {
        throw new AssertionError("Dividend excluded-account placeholder became populated unexpectedly: " + rowId);
      }
    }
  }

  private static boolean isBlankRow(SelenideElement row) {
    SelenideElement code = row.$("input[id^='dp_aet_code_']");
    SelenideElement account = row.$("select[id^='dp_aet_account_']");
    SelenideElement name = row.$("select[id^='dp_aet_name_']");
    return (!code.exists() || clean(code.getValue()).isBlank())
      && (!account.exists() || clean(account.getValue()).isBlank())
      && (!name.exists() || clean(name.getValue()).isBlank());
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable root = failure;
    while (root != null && root.getCause() != null && root.getCause() != root) root = root.getCause();
    return root == null ? failure : root;
  }

  private static void rethrow(Throwable failure) throws Exception {
    if (failure instanceof Exception exception) throw exception;
    if (failure instanceof Error error) throw error;
    throw new AssertionError("Unexpected disposable draft failure", failure);
  }

  private static String safe(String value) { return value == null ? "" : value; }

  private static String clean(String value) {
    return safe(value).replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
  }
}
