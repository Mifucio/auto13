package steps;

import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.List;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.sleep;

/**
 * Keeps draft saving on the observed business path. Dividend Payment renders
 * one blank excluded-account placeholder row. The trash action may reset that
 * row instead of removing it, so an empty retained placeholder is valid; only
 * populated unexpected rows are treated as a failure.
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

  void fillAndReliablySaveDraft(String type) throws Exception {
    if (!DIVIDEND_PAYMENT.equalsIgnoreCase(type)) {
      repair.fillAndSafelySaveDraft(type);
      return;
    }
    flow.selectSourceInstrument();
    normalizeBlankDividendExcludedRows();
    repair.safelySavePreparedDraft();
  }

  private static void normalizeBlankDividendExcludedRows() {
    List<String> blankRowIds = new ArrayList<>();
    for (SelenideElement row : $$("tr[id^='dp_account_exclude_table_row_']")) {
      if (row.exists() && row.isDisplayed() && isBlankRow(row)) {
        String id = clean(row.getAttribute("id"));
        if (!id.isBlank()) blankRowIds.add(id);
      }
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
          // The live Angular table keeps a mandatory empty row after trash.
          // This is a placeholder, not business data that must be fabricated.
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

  private static String clean(String value) {
    return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
  }
}
