package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.FileDownloadMode;
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

/** Repairs CA-29's stale historical application identity while staying fail-closed on the artifact. */
public final class Ca29RepairSteps {
  private static final Path DOWNLOADS = Path.of("build", "ca29-downloads").toAbsolutePath().normalize();
  private String observedForm = "";

  @When("I observe a current Submitted Corporate Actions application with form {string}")
  public void observeCurrentSubmittedApplication(String form) {
    if (url() == null || !url().contains("/corporate-actions")) {
      throw new AssertionError("CA-29 expected Corporate Actions list route, got " + url());
    }
    long deadline = System.currentTimeMillis() + Configuration.timeout;
    while (System.currentTimeMillis() < deadline) {
      for (SelenideElement row : $$("tbody tr")) {
        if (!row.isDisplayed()) continue;
        String text = clean(row.getText());
        if (text.toLowerCase(Locale.ROOT).contains("submitted")
            && text.toLowerCase(Locale.ROOT).contains(clean(form).toLowerCase(Locale.ROOT))) {
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

    clearDirectory(DOWNLOADS);
    String previousFolder = Configuration.downloadsFolder;
    FileDownloadMode previousMode = Configuration.fileDownload;
    java.io.File returned = null;
    Throwable directFailure = null;
    long started = System.currentTimeMillis();
    try {
      Configuration.downloadsFolder = DOWNLOADS.toString();
      Configuration.fileDownload = FileDownloadMode.FOLDER;
      try {
        returned = controls.get(0).download();
      } catch (Throwable error) {
        directFailure = error;
        try {
          executeJavaScript(
            "arguments[0].scrollIntoView({block:'center',inline:'center'}); arguments[0].click();",
            controls.get(0).getWrappedElement());
        } catch (Throwable fallbackFailure) {
          if (directFailure != fallbackFailure) directFailure.addSuppressed(fallbackFailure);
        }
      }

      if (returned != null && returned.isFile() && returned.length() > 0) return;

      long deadline = System.currentTimeMillis() + 30000;
      while (System.currentTimeMillis() < deadline) {
        Path artifact = newestNonEmptyFile(DOWNLOADS, started - 1000);
        if (artifact != null) return;
        sleep(200);
      }
    } finally {
      Configuration.downloadsFolder = previousFolder;
      Configuration.fileDownload = previousMode;
    }

    String failureType = directFailure == null ? "none" : directFailure.getClass().getSimpleName();
    throw new AssertionError("CA-29 product boundary: Download Fillable PDF form produced no non-empty printout artifact"
      + "; current_form=" + observedForm + "; download_failure=" + failureType);
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
        .filter(path -> {
          try {
            return Files.size(path) > 0 && Files.getLastModifiedTime(path).toMillis() >= minModified;
          } catch (Exception ignored) {
            return false;
          }
        })
        .max(Comparator.comparingLong(path -> {
          try { return path.toFile().lastModified(); } catch (Throwable ignored) { return 0L; }
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
    } catch (java.io.IOException error) {
      throw new AssertionError("CA-29 could not clear download directory " + directory, error);
    }
  }

  private static String clean(String value) {
    return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
  }
}