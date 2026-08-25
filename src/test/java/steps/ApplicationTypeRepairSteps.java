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

/** Selects a Corporate Actions type only inside the live chooser modal. */
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
      SelenideElement target = awaitTypeTarget(type, 15000);
      if (target == null) {
        SelenideElement modal = singleVisibleTypeModal();
        lastInventory = modal == null ? "<modal unavailable>" : modalInventory(modal);
        if (attempt < 3) {
          reopenApplicationTypeModal();
          continue;
        }
        throw new AssertionError("Observed application type '" + type
          + "' did not appear after async chooser loading; url=" + url()
          + "; modalTypes=" + lastInventory);
      }

      target.scrollIntoView("{block:'center',inline:'center'}").click();
      flow.setAppType(type);
      if (awaitApplicationData(10000)) return;
      try { lastBody = $("body").getText(); } catch (Throwable ignored) { }

      if (attempt < 3) {
        System.out.println("APPLICATION_TYPE_RETRY type=" + type + " attempt=" + attempt + " url=" + url());
        reopenApplicationTypeModal();
      }
    }

    throw new AssertionError("Selecting observed application type '" + type
      + "' did not render Application data after retries; url=" + url()
      + "; body=" + trim(lastBody, 1000) + "; modalTypes=" + lastInventory);
  }

  private static SelenideElement awaitTypeTarget(String type, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      List<SelenideElement> modals = visibleTypeModals();
      if (modals.size() > 1) {
        throw new AssertionError("Expected one visible Choose application type modal, found " + modals.size());
      }
      if (modals.size() == 1) {
        SelenideElement modal = modals.get(0);
        List<SelenideElement> labels = new ArrayList<>();
        for (SelenideElement label : modal.$$("label.form-check-label")) {
          if (label.isDisplayed() && clean(type).equalsIgnoreCase(clean(label.getText()))) labels.add(label);
        }
        if (!labels.isEmpty()) return labels.get(labels.size() - 1);

        List<SelenideElement> rows = typeRows(modal, type);
        if (!rows.isEmpty()) return typeLabelCell(rows.get(rows.size() - 1), type);
      }
      sleep(100);
    }
    return null;
  }

  private static void reopenApplicationTypeModal() {
    // No application has been saved at this point. Returning to the list is a
    // deterministic reset for an empty/malformed chooser or unsaved shell.
    open("/corporate-actions");
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 15000);
    while (System.currentTimeMillis() < deadline) {
      List<SelenideElement> create = exactVisibleControls("Create Application");
      if (create.size() == 1) {
        create.get(0).click();
        long modalDeadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < modalDeadline) {
          if (singleVisibleTypeModal() != null) return;
          sleep(100);
        }
        break;
      }
      sleep(100);
    }
    throw new AssertionError("Could not reopen Create Application chooser; url=" + url());
  }

  private static SelenideElement singleVisibleTypeModal() {
    List<SelenideElement> modals = visibleTypeModals();
    return modals.size() == 1 ? modals.get(0) : null;
  }

  private static List<SelenideElement> visibleTypeModals() {
    List<SelenideElement> result = new ArrayList<>();
    for (SelenideElement modal : $$("ngb-modal-window,[role=dialog],.modal.show")) {
      if (modal.isDisplayed() && clean(modal.getText()).contains("Choose application type")) result.add(modal);
    }
    return result;
  }

  private static List<SelenideElement> typeRows(SelenideElement modal, String type) {
    List<SelenideElement> result = new ArrayList<>();
    String wanted = clean(type);
    for (SelenideElement row : modal.$$(".modal-body .row")) {
      if (row.isDisplayed() && wanted.equalsIgnoreCase(clean(row.getText()))) result.add(row);
    }
    return result;
  }

  private static SelenideElement typeLabelCell(SelenideElement row, String type) {
    for (SelenideElement cell : row.$$("label.form-check-label,.col,.col-auto,.col-1,div")) {
      if (cell.isDisplayed() && clean(type).equalsIgnoreCase(clean(cell.getText()))) return cell;
    }
    return row;
  }

  private static boolean awaitApplicationData(long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      try {
        String current = url();
        if (current != null && current.contains("/application-form//country/")) return false;
        String body = $("body").getText();
        if ($("form").isDisplayed() && visibleTypeModals().isEmpty() && body.contains("Application data")) return true;
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
    for (SelenideElement label : modal.$$("label.form-check-label,.modal-body .row")) {
      if (!label.isDisplayed()) continue;
      String value = clean(label.getText());
      if (!value.isBlank() && !values.contains(value)) values.add(value);
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
