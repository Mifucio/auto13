package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.And;

import java.util.ArrayList;
import java.util.List;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.WebDriverRunner.url;

/**
 * Selects a Corporate Actions application type only inside the live
 * "Choose application type" modal. The background list contains form-name
 * filters with the same labels, so global text matching is intentionally
 * forbidden here.
 */
public final class ApplicationTypeRepairSteps {
  private final DisposableDividendSteps flow;

  public ApplicationTypeRepairSteps(DisposableDividendSteps flow) {
    this.flow = flow;
  }

  @And("I choose the observed {string} application type")
  public void chooseObservedApplicationType(String type) {
    String lastInventory = "";
    String lastBody = "";

    for (int attempt = 1; attempt <= 3; attempt++) {
      SelenideElement modal = awaitSingleTypeModal();
      List<SelenideElement> rows = typeRows(modal, type);
      lastInventory = modalInventory(modal);
      if (rows.isEmpty()) {
        throw new AssertionError("Observed application type '" + type
          + "' did not appear in the Choose application type modal; url=" + url()
          + "; modalTypes=" + lastInventory);
      }

      // The captured modal is a clickable .row with a .col label. The old
      // generated suite clicked the inner label and succeeded. Clicking the
      // parent row through Javascript occasionally navigated to
      // /application-form//country/XX/new (missing application-type id),
      // leaving an empty Application data shell. Use a real pointer-style
      // click on the visible label cell so Angular receives the same event
      // target as a user click.
      SelenideElement row = rows.get(rows.size() - 1);
      SelenideElement target = typeLabelCell(row, type);
      target.scrollIntoView("{block:'center',inline:'center'}").click();
      flow.setAppType(type);

      if (awaitApplicationData(10000)) return;
      try { lastBody = $("body").getText(); } catch (Throwable ignored) { }

      if (attempt < 3) {
        System.out.println("APPLICATION_TYPE_RETRY type=" + type + " attempt=" + attempt
          + " url=" + url());
        reopenApplicationTypeModal();
      }
    }

    throw new AssertionError("Selecting observed application type '" + type
      + "' did not render Application data after retries; url=" + url()
      + "; body=" + trim(lastBody, 1000)
      + "; modalTypes=" + lastInventory);
  }

  private static SelenideElement awaitSingleTypeModal() {
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 15000);
    while (System.currentTimeMillis() < deadline) {
      List<SelenideElement> modals = visibleTypeModals();
      if (modals.size() == 1) return modals.get(0);
      if (modals.size() > 1) {
        throw new AssertionError("Expected one visible Choose application type modal, found " + modals.size());
      }
      sleep(100);
    }
    throw new AssertionError("Choose application type modal did not become visible; url=" + url());
  }

  private static void reopenApplicationTypeModal() {
    // A malformed new-application shell is unsaved and disposable. Prefer its
    // exact Discard action; if the shell does not expose it, return to the list
    // directly. No application has been saved at this point.
    List<SelenideElement> discard = exactVisibleControls("Discard");
    if (discard.size() == 1) {
      discard.get(0).click();
      long leaveDeadline = System.currentTimeMillis() + 5000;
      while (System.currentTimeMillis() < leaveDeadline) {
        if (url() != null && !url().contains("/application-form/")) break;
        sleep(100);
      }
    }
    if (url() == null || url().contains("/application-form/")) open("/corporate-actions");

    long listDeadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 15000);
    while (System.currentTimeMillis() < listDeadline) {
      List<SelenideElement> create = exactVisibleControls("Create Application");
      if (create.size() == 1) {
        create.get(0).click();
        awaitSingleTypeModal();
        return;
      }
      sleep(100);
    }
    throw new AssertionError("Could not reopen Create Application after malformed application-type navigation; url=" + url());
  }

  private static List<SelenideElement> visibleTypeModals() {
    List<SelenideElement> result = new ArrayList<>();
    for (SelenideElement modal : $$("ngb-modal-window,[role=dialog],.modal.show")) {
      if (!modal.isDisplayed()) continue;
      String text = clean(modal.getText());
      if (text.contains("Choose application type")) result.add(modal);
    }
    return result;
  }

  private static List<SelenideElement> typeRows(SelenideElement modal, String type) {
    List<SelenideElement> result = new ArrayList<>();
    String wanted = clean(type);
    for (SelenideElement row : modal.$$(".modal-body .row")) {
      if (!row.isDisplayed()) continue;
      if (wanted.equalsIgnoreCase(clean(row.getText()))) result.add(row);
    }
    return result;
  }

  private static SelenideElement typeLabelCell(SelenideElement row, String type) {
    List<SelenideElement> matches = new ArrayList<>();
    for (SelenideElement cell : row.$$(".col,.col-auto,.col-1,div")) {
      if (!cell.isDisplayed()) continue;
      if (clean(type).equalsIgnoreCase(clean(cell.getText()))) matches.add(cell);
    }
    if (!matches.isEmpty()) return matches.get(0);
    return row;
  }

  private static boolean awaitApplicationData(long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      try {
        String current = url();
        // This exact route was captured in the stopped run. It cannot render a
        // form because the application-type segment is empty; retry immediately
        // instead of burning the full element timeout on an impossible state.
        if (current != null && current.contains("/application-form//country/")) return false;

        String body = $("body").getText();
        boolean formVisible = $("form").isDisplayed();
        boolean modalGone = visibleTypeModals().isEmpty();
        if (formVisible && modalGone && body.contains("Application data")) return true;
      } catch (Throwable ignored) { }
      sleep(100);
    }
    return false;
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

  private static String modalInventory(SelenideElement modal) {
    List<String> values = new ArrayList<>();
    for (SelenideElement row : modal.$$(".modal-body .row")) {
      if (!row.isDisplayed()) continue;
      String value = clean(row.getText());
      if (!value.isBlank()) values.add(value);
    }
    return values.toString();
  }

  private static String clean(String value) {
    return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
  }

  private static String trim(String value, int max) {
    String text = clean(value);
    return text.length() <= max ? text : text.substring(0, max) + "...";
  }
}
