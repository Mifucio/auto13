package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.FileDownloadMode;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.WebElement;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.WebDriverRunner.url;

/**
 * Read-only Corporate Actions selection that follows the live Submitted list
 * instead of pinning a historical ISIN/date tuple. The list is sorted newest
 * first in the observed UI, so the first visible Submitted row for the requested
 * form is the current read-only fixture.
 */
public final class AdminCorporateActionRepairSteps {
  private static final ThreadLocal<String> SELECTED_FORM = new ThreadLocal<>();
  private static final ThreadLocal<Path> DOWNLOADED_FILE = new ThreadLocal<>();

  @When("I open the latest visible Submitted Corporate Actions application with form {string}")
  public void openLatestSubmittedApplication(String form) {
    SelenideElement row = latestSubmittedRow(form);
    SelenideElement formCell = visibleCellContaining(row, form);
    formCell.scrollIntoView("{block:'center'}").click();

    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      String current = url();
      if (current != null && current.contains("/corporate-actions/application-form/")) {
        SELECTED_FORM.set(form);
        $("body").shouldBe(visible);
        return;
      }
      sleep(100);
    }
    throw new AssertionError("Clicking latest Submitted '" + form + "' row did not open an application detail; url=" + url());
  }

  @And("I open the latest observed Corporate Actions {string} tab")
  public void openLatestObservedTab(String tab) {
    requireDetailRoute();
    if (CorporateActionsTabProbe.isActive(tab)) return;
    WebElement target = CorporateActionsTabProbe.findClickable(tab);
    if (target == null) {
      throw new AssertionError("Application detail did not expose observed Corporate Actions tab '" + tab + "'");
    }
    CorporateActionsTabProbe.prepare(tab);
    $(target).scrollIntoView("{block:'center',inline:'center'}").click();
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      if (CorporateActionsTabProbe.isActive(tab)) return;
      sleep(100);
    }
    throw new AssertionError("Corporate Actions tab '" + tab + "' never became active");
  }

  @Then("the latest observed Corporate Actions {string} tab is active")
  public void latestObservedTabIsActive(String tab) {
    requireDetailRoute();
    if (!CorporateActionsTabProbe.isActive(tab)) {
      throw new AssertionError("Expected active Corporate Actions tab '" + tab + "'");
    }
  }

  @Then("the latest observed Corporate Actions application details are visible")
  public void latestApplicationDetailsVisible() {
    requireDetailRoute();
    String body = $("body").shouldBe(visible).getText();
    String form = SELECTED_FORM.get();
    if (form != null && !normalize(body).contains(normalize(form))) {
      throw new AssertionError("Opened Corporate Actions detail does not visibly contain selected form '" + form + "'");
    }
  }

  @And("I download the latest observed Corporate Actions application")
  public void downloadLatestApplication() {
    requireDetailRoute();
    Path downloads = Path.of(Configuration.downloadsFolder).toAbsolutePath().normalize();
    clearDirectory(downloads);

    List<SelenideElement> controls = exactVisibleControls("Download");
    if (controls.size() != 1) {
      throw new AssertionError("Expected exactly one visible Download control on application detail, found " + controls.size());
    }

    FileDownloadMode previous = Configuration.fileDownload;
    try {
      Configuration.fileDownload = FileDownloadMode.FOLDER;
      java.io.File file = controls.get(0).download();
      if (file == null || !file.isFile() || file.length() == 0) {
        throw new AssertionError("Download control did not produce a non-empty file");
      }
      DOWNLOADED_FILE.set(file.toPath().toAbsolutePath().normalize());
    } finally {
      Configuration.fileDownload = previous;
    }
  }

  @Then("the latest observed Corporate Actions application download exists")
  public void latestApplicationDownloadExists() {
    Path file = DOWNLOADED_FILE.get();
    if (file == null || !Files.isRegularFile(file)) {
      throw new AssertionError("No downloaded Corporate Actions application artifact was recorded");
    }
    try {
      if (Files.size(file) == 0) throw new AssertionError("Downloaded Corporate Actions application artifact is empty: " + file);
    } catch (java.io.IOException error) {
      throw new AssertionError("Could not inspect downloaded Corporate Actions application artifact", error);
    }
  }

  private static SelenideElement latestSubmittedRow(String form) {
    if (url() == null || !url().contains("/corporate-actions")) {
      throw new AssertionError("Expected Corporate Actions list route, got " + url());
    }
    $("body").shouldBe(visible);

    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      for (SelenideElement row : $$("tbody tr")) {
        if (!row.isDisplayed()) continue;
        String text = normalize(row.getText());
        if (text.contains(normalize(form)) && text.toLowerCase(Locale.ROOT).contains("submitted")) return row;
      }
      sleep(150);
    }
    throw new AssertionError("No visible Submitted Corporate Actions row found for form '" + form + "'");
  }

  private static SelenideElement visibleCellContaining(SelenideElement row, String value) {
    List<SelenideElement> matches = new ArrayList<>();
    String wanted = normalize(value);
    for (SelenideElement cell : row.$$("td")) {
      if (cell.isDisplayed() && normalize(cell.getText()).contains(wanted)) matches.add(cell);
    }
    if (matches.isEmpty()) {
      throw new AssertionError("Submitted row did not expose a visible cell containing '" + value + "': " + normalize(row.getText()));
    }
    return matches.get(0);
  }

  private static List<SelenideElement> exactVisibleControls(String label) {
    List<SelenideElement> matches = new ArrayList<>();
    for (SelenideElement control : $$("button,a,[role=button]")) {
      if (!control.isDisplayed() || !control.isEnabled()) continue;
      if (normalize(label).equalsIgnoreCase(normalize(control.getText()))) matches.add(control);
    }
    return matches;
  }

  private static void requireDetailRoute() {
    String current = url();
    if (current == null || !current.contains("/corporate-actions/application-form/")) {
      throw new AssertionError("Expected Corporate Actions application detail route, got " + current);
    }
  }

  private static void clearDirectory(Path directory) {
    try {
      Files.createDirectories(directory);
      try (var paths = Files.walk(directory)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
          if (!path.equals(directory)) Files.deleteIfExists(path);
        }
      }
    } catch (java.io.IOException error) {
      throw new AssertionError("Could not clear download directory " + directory, error);
    }
  }

  private static String normalize(String value) {
    return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
  }
}
