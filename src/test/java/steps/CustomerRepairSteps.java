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
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.WebDriverRunner.url;

/** Repairs customer-account setup using controls observed in the live evidence. */
public final class CustomerRepairSteps {

  @When("I select the represented company card {string}")
  public void selectRepresentedCompanyCard(String company) {
    String current = url();
    if (current == null || !current.contains("/company-selection")) {
      throw new AssertionError("Expected company-selection route before choosing represented company, got " + current);
    }

    String wanted = normalize(company);
    List<SelenideElement> matches = new ArrayList<>();
    for (SelenideElement link : $$("a.stretched-link")) {
      if (!link.isDisplayed() || !link.isEnabled()) continue;
      SelenideElement card = link.parent();
      String observed = normalize(card.getText());
      if (observed.contains(wanted)) matches.add(link);
    }
    if (matches.size() != 1) {
      List<String> observedCards = new ArrayList<>();
      for (SelenideElement link : $$("a.stretched-link")) {
        if (link.isDisplayed()) observedCards.add(normalize(link.parent().getText()));
      }
      throw new AssertionError("Expected exactly one represented-company card '" + company
        + "', found " + matches.size() + "; observed=" + observedCards);
    }

    matches.get(0).scrollIntoView("{block:'center'}").click();
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      String next = url();
      if (next != null && !next.contains("/company-selection")) return;
      sleep(100);
    }
    throw new AssertionError("Represented-company selection did not leave /company-selection for " + company);
  }

  @And("I ensure customer application language is English")
  public void ensureCustomerApplicationLanguageIsEnglish() {
    ensureCustomerEnglish();
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
