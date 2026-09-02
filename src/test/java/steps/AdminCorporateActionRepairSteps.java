package steps;

import com.codeborne.selenide.Configuration;
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
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.Selenide.screenshot;
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
    String form = SELECTED_FORM.get();
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    String lastBody = "";
    while (System.currentTimeMillis() < deadline) {
      SelenideElement body = $("body");
      if (body.isDisplayed()) {
        lastBody = body.getText();
        if (form == null || normalize(lastBody).contains(normalize(form))) {
          screenshot("direct-ca-single-application-details");
          return;
        }
      }
      sleep(250);
    }
    int snippet = Math.min(lastBody.length(), 600);
    throw new AssertionError("Opened Corporate Actions detail does not visibly contain selected form '" + form
      + "; last body snippet: " + lastBody.substring(0, snippet));
  }

  @And("I download the latest observed Corporate Actions application")
  public void downloadLatestApplication() {
    requireDetailRoute();
    Path downloads = Path.of(Configuration.downloadsFolder).toAbsolutePath().normalize();
    clearDirectory(downloads);
    List<SelenideElement> controls = exactVisibleControls("Download");
    if (controls.size() != 1) throw new AssertionError("Expected exactly one visible Download control on application detail, found " + controls.size());
    java.util.Set<String> beforeSet = downloadSet(downloads);
    controls.get(0).click();
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 30000);
    Path downloadedFile = awaitNewDownload(downloads, beforeSet, deadline);
    if (downloadedFile == null) throw new AssertionError("Download control click did not produce a new non-empty file in " + downloads);
    DOWNLOADED_FILE.set(downloadedFile);
    System.out.println("CA35_DOWNLOADED_FILE " + downloadedFile + " size=" + safeSize(downloadedFile));
  }
 
  private static java.util.Set<String> downloadSet(Path folder) {
    java.util.Set<String> result = new java.util.HashSet<>();
    try (var paths = Files.list(folder)) {
      for (Path path : paths.toList()) {
        try {
          if (Files.isRegularFile(path)) result.add(path.getFileName().toString() + ":" + Files.size(path));
        } catch (java.io.IOException ignored) { }
      }
    } catch (java.io.IOException ignored) { }
    return result;
  }
 
  private static Path awaitNewDownload(Path folder, java.util.Set<String> beforeSet, long deadline) {
    Path best = null;
    while (System.currentTimeMillis() < deadline) {
      try (var paths = Files.list(folder)) {
        for (Path path : paths.toList()) {
          if (Files.isRegularFile(path)) {
            String key = path.getFileName().toString() + ":" + Files.size(path);
            if (!beforeSet.contains(key)) { best = path; break; }
          }
        }
      } catch (java.io.IOException ignored) { }
      if (best != null) break;
      sleep(250);
    }
    return best;
  }
 
  private static String safeSize(Path path) {
    try { return String.valueOf(Files.size(path)); }
    catch (java.io.IOException e) { return "?"; }
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
