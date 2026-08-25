package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.And;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
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
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 20000);
    String lastInventory = "";

    while (System.currentTimeMillis() < deadline) {
      List<SelenideElement> modals = visibleTypeModals();
      if (modals.size() == 1) {
        SelenideElement modal = modals.get(0);
        List<SelenideElement> rows = typeRows(modal, type);
        lastInventory = modalInventory(modal);
        if (!rows.isEmpty()) {
          // Some types intentionally occur more than once. Preserve the
          // historical "last" semantics, but only inside the modal.
          SelenideElement row = rows.get(rows.size() - 1);
          executeJavaScript(
            "arguments[0].scrollIntoView({block:'center',inline:'center'}); arguments[0].click();",
            row.getWrappedElement());
          flow.setAppType(type);
          awaitApplicationData(type);
          return;
        }
      } else if (modals.size() > 1) {
        throw new AssertionError("Expected one visible Choose application type modal, found " + modals.size());
      }
      sleep(100);
    }

    throw new AssertionError("Observed application type '" + type
      + "' did not appear in the Choose application type modal; url=" + url()
      + "; modalTypes=" + lastInventory);
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
      if (!row.isDisplayed() || !row.isEnabled()) continue;
      if (wanted.equalsIgnoreCase(clean(row.getText()))) result.add(row);
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

  private static void awaitApplicationData(String type) {
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 20000);
    String lastBody = "";
    while (System.currentTimeMillis() < deadline) {
      try {
        lastBody = $("body").getText();
        boolean formVisible = $("form").isDisplayed();
        boolean modalGone = visibleTypeModals().isEmpty();
        if (formVisible && modalGone && lastBody.contains("Application data")) return;
      } catch (Throwable ignored) { }
      sleep(100);
    }
    throw new AssertionError("Selecting observed application type '" + type
      + "' did not render Application data; url=" + url()
      + "; body=" + trim(lastBody, 1000));
  }

  private static String clean(String value) {
    return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
  }

  private static String trim(String value, int max) {
    String text = clean(value);
    return text.length() <= max ? text : text.substring(0, max) + "...";
  }
}
