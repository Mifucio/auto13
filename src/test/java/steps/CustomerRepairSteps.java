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

    Number matches = 0;
    long cardsDeadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < cardsDeadline) {
      matches = executeJavaScript(
        "const wanted=String(arguments[0]).toLowerCase();"
          + "const links=[...document.querySelectorAll('a.stretched-link')].filter(a=>{"
          + " const card=a.parentElement;"
          + " const text=(card?.innerText||'').replace(/\\s+/g,' ').trim().toLowerCase();"
          + " return !!card && card.getClientRects().length>0 && text.includes(wanted);"
          + "});"
          + "if(links.length===1) links[0].click(); return links.length;",
        normalize(company).toLowerCase(Locale.ROOT));
      if (matches != null && matches.intValue() != 0) break;
      sleep(100);
    }

    if (matches == null || matches.intValue() != 1) {
      String observed = executeJavaScript(
        "return [...document.querySelectorAll('a.stretched-link')].map(a=>(a.parentElement?.innerText||'')"
          + ".replace(/\\s+/g,' ').trim()).filter(Boolean).join(' | ')");
      throw new AssertionError("Expected exactly one represented-company card '" + company
        + "', found " + (matches == null ? 0 : matches.intValue()) + "; observed=" + observed);
    }

    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      String next = url();
      if (next != null && !next.contains("/company-selection") && !next.contains("/login")) return;
      sleep(100);
    }
    throw new AssertionError("Represented-company selection did not leave /company-selection for " + company
      + "; url=" + url());
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
