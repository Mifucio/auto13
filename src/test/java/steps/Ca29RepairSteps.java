package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.WebDriverRunner.url;

/** Repairs CA-29 against the live two-step Fillable PDF download flow. */
public final class Ca29RepairSteps {
  private String observedForm = "";
  private Path downloadedPrintout;

  @When("I observe a current Submitted Corporate Actions application with form {string}")
  public void observeCurrentSubmittedApplication(String form) {
    if (url() == null || !url().contains("/corporate-actions")) {
      throw new AssertionError("CA-29 expected Corporate Actions list route, got " + url());
    }
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      for (SelenideElement row : $$("tbody tr")) {
        if (!row.isDisplayed()) continue;
        String text = clean(row.getText()).toLowerCase(Locale.ROOT);
        if (text.contains("submitted") && text.contains(clean(form).toLowerCase(Locale.ROOT))) {
          observedForm = form;
          return;
        }
      }
      sleep(150);
    }
    throw new AssertionError("CA-29 found no current visible Submitted application with form '" + form + "'");
  }

  @Then("the current Corporate Actions Fillable PDF printout download exists")
  public void currentFillablePdfPrintoutDownloadExists() {
    if (observedForm.isBlank()) {
      throw new AssertionError("CA-29 printout probe ran without a current Submitted application observation");
    }

    List<SelenideElement> controls = exactVisibleControls("Download Fillable PDF form");
    if (controls.size() != 1) {
      throw new AssertionError("CA-29 expected exactly one visible Download Fillable PDF form control, found " + controls.size());
    }

    Path downloads = Path.of(Configuration.downloadsFolder).toAbsolutePath().normalize();
    clearDirectory(downloads);
    downloadedPrintout = null;
    long started = System.currentTimeMillis();

    // Live evidence shows this control opens a form-type chooser; it is not a
    // direct download link. Click it normally, choose the observed form in the
    // modal, then wait for Chrome's real download in the already configured
    // download directory.
    executeJavaScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();",
      controls.get(0).getWrappedElement());
    SelenideElement modal = awaitChooseTypeModal();
    List<SelenideElement> typeRows = exactTypeRows(modal, observedForm);
    if (typeRows.isEmpty()) {
      throw new AssertionError("CA-29 Fillable PDF chooser exposed no observed form '" + observedForm
        + "'; options=" + modalInventory(modal));
    }
    SelenideElement selected = typeRows.get(typeRows.size() - 1);
    executeJavaScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();",
      selected.getWrappedElement());

    long deadline = System.currentTimeMillis() + 30000;
    while (System.currentTimeMillis() < deadline) {
      Path artifact = newestNonEmptyFile(downloads, started - 1000);
      if (artifact != null) {
        downloadedPrintout = artifact;
        return;
      }
      sleep(200);
    }

    throw new AssertionError("CA-29 product boundary: Fillable PDF chooser completed but produced no non-empty printout"
      + "; current_form=" + observedForm + "; downloads=" + downloads);
  }

  private static SelenideElement awaitChooseTypeModal() {
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 15000);
    while (System.currentTimeMillis() < deadline) {
      List<SelenideElement> matches = new ArrayList<>();
      for (SelenideElement modal : $$("ngb-modal-window,[role=dialog],.modal.show")) {
        if (modal.isDisplayed() && clean(modal.getText()).contains("Choose application type")) matches.add(modal);
      }
      if (matches.size() == 1) return matches.get(0);
      if (matches.size() > 1) {
        throw new AssertionError("CA-29 expected one visible application-type chooser, found " + matches.size());
      }
      sleep(100);
    }
    throw new AssertionError("CA-29 Download Fillable PDF form did not open the observed application-type chooser");
  }

  private static List<SelenideElement> exactTypeRows(SelenideElement modal, String expected) {
    List<SelenideElement> result = new ArrayList<>();
    String wanted = clean(expected);
    for (SelenideElement row : modal.$$(".modal-body .row")) {
      if (row.isDisplayed() && wanted.equalsIgnoreCase(clean(row.getText()))) result.add(row);
    }
    return result;
  }

  private static String modalInventory(SelenideElement modal) {
    List<String> result = new ArrayList<>();
    for (SelenideElement row : modal.$$(".modal-body .row")) {
      if (!row.isDisplayed()) continue;
      String text = clean(row.getText());
      if (!text.isBlank()) result.add(text);
    }
    return result.toString();
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

  private static Path newestNonEmptyFile(Path directory, long minModified) {
    try (var stream = Files.walk(directory)) {
      return stream.filter(Files::isRegularFile)
        .filter(path -> !path.getFileName().toString().endsWith(".part"))
        .filter(path -> {
          try {
            return Files.size(path) > 0 && Files.getLastModifiedTime(path).toMillis() >= minModified;
          } catch (Exception ignored) {
            return false;
          }
        })
        .max(Comparator.comparingLong(path -> {
          try { return Files.getLastModifiedTime(path).toMillis(); }
          catch (Exception ignored) { return 0L; }
        })).orElse(null);
    } catch (Exception ignored) {
      return null;
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
    } catch (Exception error) {
      throw new AssertionError("CA-29 could not clear download directory " + directory, error);
    }
  }

  private static String clean(String value) {
    return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
  }
}
