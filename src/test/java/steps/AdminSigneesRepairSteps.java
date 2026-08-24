package steps;

import com.codeborne.selenide.Configuration;
import io.cucumber.java.en.Then;

import java.util.Locale;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.WebDriverRunner.url;

/** Concrete replacement for the generated `signees_list_visible` pseudo-oracle. */
public final class AdminSigneesRepairSteps {
  @Then("the admin signees list is visible")
  public void adminSigneesListVisible() {
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      String body = $("body").shouldBe(visible).getText().toLowerCase(Locale.ROOT);
      boolean semantic = body.contains("signee") || body.contains("signer");
      boolean structured = $$("table tbody tr, [role=row], ul li").filterBy(visible).size() > 0;
      if (semantic && structured) return;
      sleep(200);
    }
    throw new AssertionError("Admin signing flow did not expose a populated signees/signers surface; url=" + url());
  }
}
