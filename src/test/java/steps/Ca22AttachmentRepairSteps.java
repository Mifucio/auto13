package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.WebDriverRunner.url;

/** CA-22 live binding for the Bonus Issue "Other" document slot. */
public final class Ca22AttachmentRepairSteps {
  private static final String FIXTURE = "/fixtures/ca22-disposable-attachment.txt";
  private static final String INPUT_ID = "bi_doc_4";
  private Path stagedFixture;

  @And("I stage the harmless CA-22 fixture in the observed Other document input")
  public void stageFixtureInObservedOtherInput() {
    stagedFixture = fixturePath();
    SelenideElement input = awaitOtherInput();
    input.uploadFile(stagedFixture.toFile());
  }

  @Then("the CA-22 Other document input contains exactly one staged disposable attachment")
  public void otherInputContainsOneFixture() {
    if (stagedFixture == null) throw new AssertionError("CA-22 fixture was not staged before verification");
    SelenideElement input = awaitOtherInput();
    Number count = executeJavaScript("return arguments[0].files ? arguments[0].files.length : 0;",
      input.getWrappedElement());
    if (count == null || count.intValue() != 1) {
      throw new AssertionError("CA-22 Other input expected one staged file, found "
        + (count == null ? 0 : count.intValue()));
    }
    String value = input.getValue();
    String expected = stagedFixture.getFileName().toString().toLowerCase(Locale.ROOT);
    if (value == null || !value.toLowerCase(Locale.ROOT).endsWith(expected)) {
      throw new AssertionError("CA-22 Other input did not retain fixture " + stagedFixture.getFileName());
    }
  }

  @And("I discard the unsaved CA-22 draft without saving")
  public void discardUnsavedDraft() {
    Number count = executeJavaScript(
      "const wanted='discard';"
        + "const c=[...document.querySelectorAll('button,a,[role=button],input[type=button]')]"
        + ".filter(e=>e.offsetParent!==null&&!e.disabled)"
        + ".filter(e=>String(e.innerText||e.value||e.getAttribute('aria-label')||'').replace(/\\s+/g,' ').trim().toLowerCase()===wanted);"
        + "if(c.length===1){c[0].click();} return c.length;");
    if (count == null || count.intValue() != 1) {
      throw new AssertionError("CA-22 expected exactly one observed Discard control, found "
        + (count == null ? 0 : count.intValue()));
    }
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 10000);
    while (System.currentTimeMillis() < deadline) {
      String current = url();
      if (current == null || !current.contains("/corporate-actions/application-form")) return;
      sleep(100);
    }
    stagedFixture = null;
  }

  private static SelenideElement awaitOtherInput() {
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 10000);
    SelenideElement input = $("#" + INPUT_ID);
    while (System.currentTimeMillis() < deadline) {
      if (input.exists() && input.isEnabled()) return input;
      sleep(100);
    }
    throw new AssertionError("CA-22 Bonus Issue did not expose observed Other document input #" + INPUT_ID);
  }

  private static Path fixturePath() {
    URL resource = Ca22AttachmentRepairSteps.class.getResource(FIXTURE);
    if (resource == null) throw new AssertionError("Missing CA-22 fixture " + FIXTURE);
    try {
      Path path = Path.of(resource.toURI()).toAbsolutePath().normalize();
      if (!Files.isRegularFile(path) || Files.size(path) <= 0) {
        throw new AssertionError("CA-22 fixture is missing or empty: " + path);
      }
      return path;
    } catch (Exception error) {
      throw new AssertionError("Could not resolve CA-22 fixture", error);
    }
  }
}
