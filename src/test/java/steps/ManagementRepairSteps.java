package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.Then;
import org.openqa.selenium.StaleElementReferenceException;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.WebDriverRunner.url;

/** Read-only management assertions that re-query the Angular DOM after rerenders. */
public final class ManagementRepairSteps {

  @Then("the opened external role {string} editor remains visible without saving")
  public void openedExternalRoleEditorRemainsVisible(String roleName) {
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 20000);
    Throwable last = null;
    while (System.currentTimeMillis() < deadline) {
      try {
        String current = url();
        if (current == null || !current.matches("https://eservicesdevint\\.sets\\.lv/external/admin/authority-rights/[0-9]+/edit(?:[/?#].*)?")) {
          throw new AssertionError("Expected external role editor route, got " + current);
        }
        $("h1").shouldBe(visible);
        $("form").shouldBe(visible);
        String body = $("body").shouldBe(visible).getText();
        if (body != null && body.contains(roleName)) return;
        for (SelenideElement field : $$("input,textarea")) {
          if (!field.exists()) continue;
          String value = field.getValue();
          if (roleName.equals(value == null ? "" : value.trim())) return;
        }
      } catch (StaleElementReferenceException stale) {
        last = stale;
      }
      sleep(100);
    }
    throw new AssertionError("External role editor did not stably expose role '" + roleName + "'; url=" + url(), last);
  }
}
