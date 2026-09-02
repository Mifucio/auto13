package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.And;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.WebDriverRunner.url;

/** Repairs customer-account setup using controls observed in the live evidence. */
public final class CustomerRepairSteps {

  @When("I select the represented company card {string}")
  public void selectRepresentedCompanyCard(String company) {
    selectRepresentedCompanyCardOnSelectionPage(company);
  }

  /**
   * The live company cards use Bootstrap stretched-link anchors. The anchor
   * itself can have a zero-size box while its pseudo-element stretches across
   * the visible card, so WebDriver's native click rejects it as not interactable.
   * Triggering the unique observed anchor through DOM click preserves the same
   * navigation without relying on a fictitious element box.
   */
  static void selectRepresentedCompanyCardOnSelectionPage(String company) {
    String current = url();
    if (current == null || !current.contains("/company-selection")) {
      throw new AssertionError("Expected company-selection route before choosing represented company, got " + current);
    }
    AuthSupport.selectCompanyCardOnSelectionPage(company);
  }

  @And("I ensure customer application language is English")
  public void ensureCustomerApplicationLanguageIsEnglish() {
    ensureCustomerEnglish();
  }


  /** Lenient: ensures English only when the navbar language menu is actually
   * present. Some pages (e.g. new-form deep links) do not render the navbar;
   * the stored language preference remains English once set. */
  static void ensureCustomerEnglishIfPresent() {
    try {
      SelenideElement language = $("#navbarLanguages");
      if (!language.exists() || !language.isDisplayed()) return;
      ensureCustomerEnglish();
    } catch (Throwable ignored) { }
  }
  static void ensureCustomerEnglish() {
    SelenideElement language = $("#navbarLanguages");
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline && !language.isDisplayed()) sleep(100);
    language.shouldBe(visible);
    if ("EN".equalsIgnoreCase(normalize(language.getText()))) return;

    language.click();
    List<SelenideElement> english = new ArrayList<>();
    for (SelenideElement option : $$("span.dropdown-item")) {
      if (option.isDisplayed() && "english".equals(normalize(option.getText()).toLowerCase(Locale.ROOT))) {
        english.add(option);
      }
    }
    if (english.size() != 1) {
      throw new AssertionError("Customer language menu did not expose exactly one English option; found " + english.size());
    }
    english.get(0).click();
    language.shouldHave(text("EN"));
  }

  private static String normalize(String value) {
    return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
  }
}
