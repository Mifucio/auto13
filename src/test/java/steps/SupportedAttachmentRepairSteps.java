package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.FileDownloadMode;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.WebElement;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.sleep;

/** Uses a PDF fixture accepted by the live attachment input. */
public final class SupportedAttachmentRepairSteps {
  private static final String FIXTURE = "/fixtures/ca22-disposable-attachment.pdf";
  private static final Path DOWNLOADS = Path.of("build", "supported-attachment-downloads").toAbsolutePath().normalize();
  private String attachmentName = "";
  private Path downloaded;

  @When("I attach the supported PDF fixture in the current application Attachments tab")
  public void attachSupportedPdf() {
    openTab("Attachments");
    Path fixture = fixturePath();
    attachmentName = fixture.getFileName().toString();
    SelenideElement input = awaitFileInput();
    input.uploadFile(fixture.toFile());

    if (!waitForAttachmentName(3000)) {
      for (String label : List.of("Upload", "Add attachment", "Save attachment", "Attach")) {
        List<SelenideElement> controls = exactVisibleControls(label);
        if (controls.size() == 1) {
          controls.get(0).click();
          break;
        }
      }
    }
    if (!waitForAttachmentName(15000)) {
      throw new AssertionError("Supported PDF fixture was not visible after upload: " + attachmentName
        + "; accept=" + input.getAttribute("accept"));
    }
  }

  @Then("the supported PDF fixture is visible in the current application")
  public void supportedPdfVisible() {
    if (attachmentName.isBlank() || !waitForAttachmentName(10000)) {
      throw new AssertionError("Supported PDF fixture is not visible in Attachments: " + attachmentName);
    }
  }

  @When("I download the supported PDF fixture from the current application")
  public void downloadSupportedPdf() {
    openTab("Attachments");
    if (!waitForAttachmentName(10000)) throw new AssertionError("Cannot download absent attachment " + attachmentName);
    SelenideElement control = attachmentDownloadControl();
    clearDirectory(DOWNLOADS);
    downloaded = null;

    String previousFolder = Configuration.downloadsFolder;
    FileDownloadMode previousMode = Configuration.fileDownload;
    try {
      Configuration.downloadsFolder = DOWNLOADS.toString();
      Configuration.fileDownload = FileDownloadMode.FOLDER;
      java.io.File returned = null;
      try { returned = control.download(); } catch (Throwable failure) {
        try { control.click(); } catch (Throwable ignored) { }
      }
      if (returned != null && returned.isFile() && returned.length() > 0) {
        downloaded = returned.toPath().toAbsolutePath().normalize();
        return;
      }
      long deadline = System.currentTimeMillis() + 20000;
      while (System.currentTimeMillis() < deadline) {
        Path file = firstNonEmptyFile(DOWNLOADS);
        if (file != null) { downloaded = file; return; }
        sleep(150);
      }
    } finally {
      Configuration.downloadsFolder = previousFolder;
      Configuration.fileDownload = previousMode;
    }
    throw new AssertionError("Supported PDF download produced no non-empty artifact");
  }

  @Then("the supported PDF fixture download exists")
  public void supportedPdfDownloadExists() {
    if (downloaded == null || !Files.isRegularFile(downloaded) || downloaded.toFile().length() <= 0) {
      throw new AssertionError("Supported PDF fixture download is missing or empty");
    }
  }

  private static void openTab(String name) {
    if (CorporateActionsTabProbe.isActive(name)) return;
    WebElement clickable = CorporateActionsTabProbe.findClickable(name);
    if (clickable == null) throw new AssertionError("No observed " + name + " tab control was found");
    CorporateActionsTabProbe.prepare(name);
    $(clickable).scrollIntoView("{block:'center',inline:'center'}").click();
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 15000);
    while (System.currentTimeMillis() < deadline) {
      if (CorporateActionsTabProbe.isActive(name)) return;
      sleep(100);
    }
    throw new AssertionError(name + " tab never became active");
  }

  private static SelenideElement awaitFileInput() {
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 15000);
    while (System.currentTimeMillis() < deadline) {
      for (SelenideElement input : $$("input[type=file]")) {
        if (input.exists() && input.isEnabled()) return input;
      }
      sleep(100);
    }
    throw new AssertionError("Attachments surface exposed no enabled file input");
  }

  private boolean waitForAttachmentName(long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      try { if (!attachmentName.isBlank() && $("body").getText().contains(attachmentName)) return true; }
      catch (Throwable ignored) { }
      sleep(100);
    }
    return false;
  }

  private SelenideElement attachmentDownloadControl() {
    List<SelenideElement> containers = new ArrayList<>();
    for (SelenideElement candidate : $$("tr,li,.card,.row,div")) {
      if (!candidate.isDisplayed()) continue;
      if (clean(candidate.getText()).contains(attachmentName)) containers.add(candidate);
    }
    containers.sort(Comparator.comparingInt(candidate -> clean(candidate.getText()).length()));
    for (SelenideElement container : containers) {
      for (SelenideElement control : container.$$("a,button,[role=button]")) {
        if (!control.isDisplayed() || !control.isEnabled()) continue;
        String clue = clean(control.getText() + " " + control.getAttribute("aria-label") + " " + control.getAttribute("title")).toLowerCase(Locale.ROOT);
        String href = clean(control.getAttribute("href"));
        if (clue.contains("download") || (!href.isBlank() && "a".equalsIgnoreCase(control.getTagName()))) return control;
      }
    }
    throw new AssertionError("Attachment is visible but no row-scoped download control was found: " + attachmentName);
  }

  private static Path fixturePath() {
    URL resource = SupportedAttachmentRepairSteps.class.getResource(FIXTURE);
    if (resource == null) throw new AssertionError("Missing supported attachment fixture " + FIXTURE);
    try {
      Path path = Path.of(resource.toURI()).toAbsolutePath().normalize();
      if (!Files.isRegularFile(path) || Files.size(path) <= 0) throw new AssertionError("Supported fixture is empty");
      return path;
    } catch (Exception error) { throw new AssertionError("Could not resolve supported PDF fixture", error); }
  }

  private static Path firstNonEmptyFile(Path directory) {
    try (var stream = Files.walk(directory)) {
      return stream.filter(Files::isRegularFile).filter(path -> path.toFile().length() > 0).findFirst().orElse(null);
    } catch (Exception ignored) { return null; }
  }

  private static void clearDirectory(Path directory) {
    try {
      Files.createDirectories(directory);
      try (var paths = Files.walk(directory)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) if (!path.equals(directory)) Files.deleteIfExists(path);
      }
    } catch (Exception error) { throw new AssertionError("Could not clear attachment download directory", error); }
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

  private static String clean(String value) {
    return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
  }
}
