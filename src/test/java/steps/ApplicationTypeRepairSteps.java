package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.And;
import org.openqa.selenium.StaleElementReferenceException;

import java.util.ArrayList;
import java.util.List;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.WebDriverRunner.url;

/** Selects a Corporate Actions type only inside the live chooser modal. */
public final class ApplicationTypeRepairSteps {
  private static final String DEFAULT_COMPANY = "AutotestLtSingleSignee";
  private final DisposableDividendSteps flow;

  public ApplicationTypeRepairSteps(DisposableDividendSteps flow) {
    this.flow = flow;
  }

  @And("I choose the observed {string} application type")
  public void chooseObservedApplicationType(String type) {
    String lastInventory = "";
    String lastBody = "";

    for (int attempt = 1; attempt <= 3; attempt++) {
      SelenideElement target = awaitTypeTargetWithStaleRetry(type, 15000);
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

      clickResolvedApplicationTypeTarget(type, target);
      System.out.println("APPLICATION_TYPE_AFTER_CLICK type=" + type + " url=" + url());
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

  private static SelenideElement awaitTypeTargetWithStaleRetry(String type, long timeoutMs) {
    try {
      return awaitTypeTarget(type, timeoutMs);
    } catch (RuntimeException error) {
      if (!isStale(error)) throw error;
      System.out.println("APPLICATION_TYPE_TARGET_REACQUIRE reason=stale_during_resolution");
      return awaitTypeTarget(type, Math.min(timeoutMs, 2000));
    }
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
        if (labels.size() > 1) {
          throw new AssertionError("Expected exactly one visible application type label for '" + type
            + "', found " + labels.size());
        }
        if (labels.size() == 1) return labels.get(0);

        List<SelenideElement> rows = typeRows(modal, type);
        if (rows.size() > 1) {
          // Multiple rows match (different form versions). Always take the
          // last one (newest version, bottom of the list).
          System.out.println("APPLICATION_TYPE_MULTIPLE_ROWS type=" + type
            + " count=" + rows.size() + " taking last (newest)");
          return typeLabelCell(rows.get(rows.size() - 1), type);
        }
        if (rows.size() == 1) return typeLabelCell(rows.get(0), type);
      }
      sleep(100);
    }
    return null;
  }

  private void reopenApplicationTypeModal() {
    // No application has been saved at this point. Returning to the list is a
    // deterministic reset for an empty/malformed chooser or unsaved shell.
    open("/corporate-actions");
    if (url() != null && !url().contains("/login") && awaitCreateApplicationChooser()) return;

    // A failed chooser transition can invalidate the customer SPA shell while
    // leaving the browser on the list route. Re-enter the authenticated
    // customer context before reopening Corporate Actions, rather than trying
    // to drive the stale shell or adding more unauthenticated route retries.
    System.out.println("APPLICATION_TYPE_AUTHENTICATED_RECOVERY");
    flow.login();
    if (url() == null || url().contains("/login")) {
      throw new AssertionError("Authenticated chooser recovery remained on login; url=" + url());
    }
    flow.selectCompany(DEFAULT_COMPANY);
    if (url() == null || url().contains("/login")) {
      throw new AssertionError("Authenticated chooser recovery lost company context; url=" + url());
    }
    flow.openCorporateActions();
    if (awaitCreateApplicationChooser()) return;

    throw new AssertionError("Could not reopen Create Application chooser after authenticated recovery; url=" + url());
  }

  private static boolean awaitCreateApplicationChooser() {
    long deadline = System.currentTimeMillis() + Math.max(15000, Math.min(Configuration.timeout, 30000));
    while (System.currentTimeMillis() < deadline) {
      List<SelenideElement> createControls = exactVisibleControls("Create Application");
      if (createControls.size() > 1) {
        throw new AssertionError("Expected exactly one visible Create Application control, found "
          + createControls.size());
      }
      if (createControls.size() == 1) {
        createControls.get(0).scrollIntoView("{block:'center',inline:'center'}").click();
        long modalDeadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < modalDeadline) {
          if (singleVisibleTypeModal() != null) return true;
          sleep(100);
        }
        break;
      }
      sleep(100);
    }
    return false;
  }

  /**
   * Click the observed chooser target once. Angular may replace the row while
   * handling the click, so post-click readiness must use route/form evidence
   * rather than querying this possibly stale WebElement again. A later outer
   * attempt reacquires the target from the newly rendered modal if needed.
   */
  private static void clickApplicationTypeTarget(SelenideElement target) {
    target.scrollIntoView("{block:'center',inline:'center'}");
    target.click();
  }

  private static void clickResolvedApplicationTypeTarget(String type, SelenideElement target) {
    try {
      System.out.println("APPLICATION_TYPE_TARGET " + executeJavaScript(
        "let e=arguments[0];let h=[];for(let i=0;e&&i<5;i++,e=e.parentElement)"
          + "h.push(e.tagName+'.'+String(e.className||'').replace(/\\s+/g,'.'));"
          + "return JSON.stringify({tag:arguments[0].tagName,className:arguments[0].className,"
          + "outerHTML:String(arguments[0].outerHTML||'').slice(0,900),hierarchy:h});",
        target.getWrappedElement()));
      clickApplicationTypeTarget(target);
    } catch (RuntimeException error) {
      if (!isStale(error)) throw error;
      System.out.println("APPLICATION_TYPE_TARGET_REACQUIRE reason=stale_during_click");
      SelenideElement freshTarget = awaitTypeTargetWithStaleRetry(type, 2000);
      if (freshTarget == null) {
        throw new AssertionError("Application type '" + type + "' disappeared while reacquiring after a stale click target");
      }
      System.out.println("APPLICATION_TYPE_TARGET_REACQUIRED type=" + type);
      clickApplicationTypeTarget(freshTarget);
    }
  }

  private static boolean isStale(Throwable error) {
    for (Throwable current = error; current != null; current = current.getCause()) {
      if (current instanceof StaleElementReferenceException) return true;
    }
    return false;
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
    List<SelenideElement> matches = new ArrayList<>();
    for (SelenideElement cell : row.$$(".col,.col-auto,.col-1,div")) {
      if (cell.isDisplayed() && clean(type).equalsIgnoreCase(clean(cell.getText()))) matches.add(cell);
    }
    if (matches.size() > 1) {
      throw new AssertionError("Expected exactly one visible application type control within the '" + type
        + "' row, found " + matches.size());
    }
    if (matches.size() == 1) return matches.get(0);
    return row;
  }

  private static boolean awaitApplicationData(long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      try {
        String current = url();
        if (current != null && current.contains("/application-form//country/")) {
          System.out.println("APPLICATION_DATA_WAIT_EXIT reason=malformed_route url=" + current);
          return false;
        }
        String body = $("body").getText();
        if ($("form").isDisplayed() && visibleTypeModals().isEmpty() && body.contains("Application data")) {
          System.out.println("APPLICATION_DATA_WAIT_EXIT reason=ready url=" + current);
          return true;
        }
      } catch (Throwable ignored) { }
      sleep(100);
    }
    System.out.println("APPLICATION_DATA_WAIT_EXIT reason=timeout url=" + url());
    return false;
  }

  private static List<SelenideElement> exactVisibleControls(String expected) {
    List<SelenideElement> result = new ArrayList<>();
    for (SelenideElement control : $$("button,a,[role=button],input[type=button],input[type=submit]")) {
      try {
        if (!control.isDisplayed() || !control.isEnabled()) continue;
        String label = clean(control.getText());
        if (label.isBlank()) label = clean(control.getAttribute("value"));
        if (label.isBlank()) label = clean(control.getAttribute("aria-label"));
        if (expected.equalsIgnoreCase(label)) result.add(control);
      } catch (Throwable stale) {
        // Angular can replace controls during polling. Reacquire on the next poll.
      }
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
