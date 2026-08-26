package steps;

import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.sleep;

/**
 * Keeps draft saving on the observed business path while avoiding the two
 * live-proven generic-helper traps: duplicate Save as Draft controls and the
 * empty default Dividend Payment excluded-account row. An empty excluded row
 * is not business data, so remove it instead of fabricating a holder code and
 * then trying to satisfy dependent account/name selects.
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
    removeBlankDividendExcludedRows();
    repair.safelySavePreparedDraft();
  }

  void fillAndReliablySaveDraft(String type) throws Exception {
    if (!DIVIDEND_PAYMENT.equalsIgnoreCase(type)) {
      repair.fillAndSafelySaveDraft(type);
      return;
    }

    // Dividend Payment has no dedicated type-specific filler. Select the source
    // instrument, remove the empty optional exclusion row, then let the existing
    // validation-driven repair fill only genuinely required scalar fields.
    flow.selectSourceInstrument();
    removeBlankDividendExcludedRows();
    repair.safelySavePreparedDraft();
  }

  private static void removeBlankDividendExcludedRows() {
    List<SelenideElement> removable = new ArrayList<>();
    for (SelenideElement row : $$("tr[id^='dp_account_exclude_table_row_']")) {
      if (!row.exists() || !row.isDisplayed()) continue;
      SelenideElement code = row.$("input[id^='dp_aet_code_']");
      SelenideElement account = row.$("select[id^='dp_aet_account_']");
      SelenideElement name = row.$("select[id^='dp_aet_name_']");
      boolean codeBlank = !code.exists() || clean(code.getValue()).isBlank();
      boolean accountBlank = !account.exists() || clean(account.getValue()).isBlank();
      boolean nameBlank = !name.exists() || clean(name.getValue()).isBlank();
      if (codeBlank && accountBlank && nameBlank) removable.add(row);
    }

    for (SelenideElement row : removable) {
      SelenideElement delete = row.$("img[alt='delete role'][type='button'], .ui-icon-trash img[type='button']");
      if (!delete.exists() || !delete.isDisplayed()) {
        throw new AssertionError("Blank Dividend excluded-account row exposed no observed delete control");
      }
      String rowId = clean(row.getAttribute("id"));
      executeJavaScript("arguments[0].click();", delete.getWrappedElement());
      long deadline = System.currentTimeMillis() + 5000;
      while (System.currentTimeMillis() < deadline) {
        if (!row.exists() || !row.isDisplayed()) break;
        sleep(100);
      }
      if (row.exists() && row.isDisplayed()) {
        throw new AssertionError("Blank Dividend excluded-account row was not removed: " + rowId);
      }
      System.out.println("DIVIDEND_EMPTY_EXCLUDED_ROW_REMOVED " + rowId);
    }
  }

  private static String clean(String value) {
    return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
  }
}
