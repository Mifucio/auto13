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

/** CA-22 live binding for the Bonus Issue Other document slot. */
public final class Ca22AttachmentRepairSteps {
  private static final String FIXTURE = "/fixtures/ca22-disposable-attachment.pdf";
  private static final String INPUT_ID = "bi_doc_4";
  private Path stagedFixture;

  @And("I stage the harmless CA-22 fixture in the observed Other document input")
  public void stageFixtureInObservedOtherInput() {
    stagedFixture = fixturePath();
    SelenideElement input = awaitOtherInput();
    executeJavaScript(
      "window.__ca22Staged=null; arguments[0].addEventListener('change',function handler(e){"
        + "const f=e.target.files; window.__ca22Staged={count:f?f.length:0,name:(f&&f.length)?f[0].name:''};"
        + "},{once:true});", input.getWrappedElement());
    input.uploadFile(stagedFixture.toFile());
  }

  @Then("the CA-22 Other document input contains exactly one staged disposable attachment")
  public void otherInputContainsOneFixture() {
    if (stagedFixture == null) throw new AssertionError("CA-22 fixture was not staged before verification");
    String expected = stagedFixture.getFileName().toString();
    Object captured = executeJavaScript("const x=window.__ca22Staged; return x ? JSON.stringify(x) : '';");
    String evidence = captured == null ? "" : captured.toString();
    if (evidence.contains("\"count\":1") && evidence.contains("\"name\":\"" + jsJson(expected) + "\"")) return;

    SelenideElement input = awaitOtherInput();
    Number count = executeJavaScript("return arguments[0].files ? arguments[0].files.length : 0;", input.getWrappedElement());
    String value = input.getValue();
    boolean nativeEvidence = count != null && count.intValue() == 1
      && value != null && value.toLowerCase(Locale.ROOT).endsWith(expected.toLowerCase(Locale.ROOT));
    if (!nativeEvidence) {
      throw new AssertionError("CA-22 upload did not carry the supported PDF fixture; captured=" + evidence
        + "; nativeCount=" + (count == null ? 0 : count.intValue()) + "; accept=" + input.getAttribute("accept"));
    }
  }

  @And("I discard the unsaved CA-22 draft without saving")
  public void discardUnsavedDraft() {
    Number count = executeJavaScript(
      "const c=[...document.querySelectorAll('button,a,[role=button],input[type=button]')]"
        + ".filter(e=>e.offsetParent!==null&&!e.disabled)"
        + ".filter(e=>String(e.innerText||e.value||e.getAttribute('aria-label')||'').replace(/\\s+/g,' ').trim().toLowerCase()==='discard');"
        + "if(c.length===1)c[0].click(); return c.length;");
    if (count == null || count.intValue() != 1) {
      throw new AssertionError("CA-22 expected exactly one observed Discard control, found " + (count == null ? 0 : count.intValue()));
    }
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 10000);
    while (System.currentTimeMillis() < deadline) {
      String current = url();
      if (current == null || !current.contains("/corporate-actions/application-form")) {
        stagedFixture = null;
        return;
      }
      sleep(100);
    }
    throw new AssertionError("CA-22 discard did not leave the unsaved application form; url=" + url());
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
      if (!Files.isRegularFile(path) || Files.size(path) <= 0) throw new AssertionError("CA-22 fixture is missing or empty: " + path);
      return path;
    } catch (Exception error) {
      throw new AssertionError("Could not resolve CA-22 fixture", error);
    }
  }

  private static String jsJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
