package steps;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.FileDownloadMode;
import com.codeborne.selenide.SelenideElement;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.WebDriverRunner.url;

/** Repairs CA-29 against the live two-step Fillable PDF download flow. */
public final class Ca29RepairSteps {
  private static final Pattern URL_PATTERN = Pattern.compile("(?i)https?://[^\\s\"'<>]+");
  private static final Pattern QUERY_SECRET_PATTERN = Pattern.compile(
    "(?i)([?&](?:token|access_token|refresh_token|id_token|api[_-]?key|password|secret|authorization|cookie|otp|personal[_-]?code)=)[^&\\s\"']+");
  private static final Pattern SENSITIVE_ASSIGNMENT_PATTERN = Pattern.compile(
    "(?i)(\\b(?:authorization|cookie|set-cookie|token|access_token|refresh_token|id_token|api[_-]?key|password|secret|otp|personal[_-]?code)\\b\\s*\"?\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^\\s,;}\\]]+)");
  private static final Pattern HTML_ATTRIBUTE_PATTERN = Pattern.compile(
    "(?i)(\\s+(?:href|download|src|action|value|name|autocomplete|title|(?:data|aria)-[\\w:.-]+)\\s*=\\s*)(['\"])(.*?)\\2");
  private static final Pattern JSON_ATTRIBUTE_PATTERN = Pattern.compile(
    "(?i)(\"(?:href|download|src|action|value)\"\\s*:\\s*\")(.*?)(\")");
  private static final Pattern JSON_OUTER_HTML_PATTERN = Pattern.compile(
    "(?i)(\"outerHTML\"\\s*:\\s*\")(?:\\\\.|[^\"\\\\])*(\")");
  private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
    "(?i)\\b(?:bearer|basic)\\s+[A-Za-z0-9._~+/=-]{8,}");
  private static final Pattern JWT_PATTERN = Pattern.compile(
    "\\b[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b");
  private String observedForm = "";
  private Path downloadedPrintout;

  @When("I observe a current Submitted Corporate Actions application with form {string}")
  public void observeCurrentSubmittedApplication(String form) {
    if (url() == null || !url().contains("/corporate-actions")) {
      throw new AssertionError("CA-29 expected Corporate Actions list route, got " + redactUrl(url()));
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
    throw new AssertionError("CA-29 found no current Submitted application with form '" + form + "'");
  }

  @Then("the current Corporate Actions Fillable PDF printout download exists")
  public void currentFillablePdfPrintoutDownloadExists() {
    if (observedForm.isBlank()) throw new AssertionError("CA-29 printout probe ran without application observation");
    List<SelenideElement> controls = exactVisibleControls("Download Fillable PDF form");
    if (controls.size() != 1) throw new AssertionError("CA-29 expected one Download Fillable PDF form control, found " + controls.size());

    Path downloads = Path.of(Configuration.downloadsFolder).toAbsolutePath().normalize();
    clearDirectory(downloads);
    downloadedPrintout = null;
    executeJavaScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", controls.get(0).getWrappedElement());

    SelenideElement modal = awaitChooseTypeModal();
    List<SelenideElement> typeRows = exactTypeRows(modal, observedForm);
    if (typeRows.isEmpty()) {
      throw new AssertionError("CA-29 chooser exposed no form '" + observedForm + "'; options=" + modalInventory(modal));
    }
    SelenideElement selected = typeRows.get(typeRows.size() - 1);
    if (!"LABEL".equalsIgnoreCase(tagOf(selected))) {
      selected = exactTypeTarget(selected, observedForm);
    }
    System.out.println("CA29_DIAGNOSTIC locator=" + locatorSummary(selected));

    String previousFolder = Configuration.downloadsFolder;
    FileDownloadMode previousMode = Configuration.fileDownload;
    java.io.File returned = null;
    Throwable directFailure = null;
    long started = System.currentTimeMillis();
    String traceEvidenceFile = "";
    try {
      Configuration.downloadsFolder = downloads.toString();
      Configuration.fileDownload = FileDownloadMode.FOLDER;
      AdminSteps.installCa29Trace();
      if (isDirectDownloadTarget(selected)) {
        try {
          returned = selected.download();
        } catch (Throwable failure) {
          directFailure = failure;
        }
      }
      if (returned == null) {
        // The live chooser renders application types as Angular rows, not
        // download links. Clicking the option opens the country chooser first.
        clickRendered(selected);
        SelenideElement countryModal = awaitChooseCountryModal();
        List<SelenideElement> countries = exactTypeRows(countryModal, "Latvia");
        if (countries.isEmpty()) {
          throw new AssertionError("CA-29 country chooser exposed no Latvia option; options="
            + modalInventory(countryModal));
        }
        clickRendered(exactCountryTarget(countries.get(countries.size() - 1)));
      }
      if (returned != null && returned.isFile() && returned.length() > 0) {
        downloadedPrintout = returned.toPath().toAbsolutePath().normalize();
        return;
      }
      long deadline = System.currentTimeMillis() + 30000;
      while (System.currentTimeMillis() < deadline) {
        Path artifact = newestNonEmptyFile(downloads, started - 1000);
        if (artifact != null) {
          downloadedPrintout = artifact;
          return;
        }
        sleep(200);
      }
    } finally {
      Configuration.downloadsFolder = previousFolder;
      Configuration.fileDownload = previousMode;
      traceEvidenceFile = captureCa29TraceEvidence(selected, directFailure);
    }

      throw new AssertionError("CA-29 Fillable PDF chooser produced no artifact; form=" + sanitizeDiagnosticText(observedForm)
        + "; direct_failure=" + (directFailure == null ? "none" : directFailure.getClass().getSimpleName())
        + "; evidence=" + traceEvidenceFile);
  }

  private static SelenideElement awaitChooseTypeModal() {
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 15000);
    while (System.currentTimeMillis() < deadline) {
      List<SelenideElement> matches = new ArrayList<>();
      for (SelenideElement modal : $$("ngb-modal-window,[role=dialog],.modal.show")) {
        if (modal.isDisplayed() && clean(modal.getText()).contains("Choose application type")) matches.add(modal);
      }
      if (matches.size() == 1) return matches.get(0);
      if (matches.size() > 1) throw new AssertionError("CA-29 expected one chooser, found " + matches.size());
      sleep(100);
    }
    throw new AssertionError("CA-29 Download Fillable PDF form did not open chooser");
  }

  private static SelenideElement awaitChooseCountryModal() {
    long deadline = System.currentTimeMillis() + Math.min(Configuration.timeout, 15000);
    while (System.currentTimeMillis() < deadline) {
      List<SelenideElement> matches = new ArrayList<>();
      for (SelenideElement modal : $$("ngb-modal-window,[role=dialog],.modal.show")) {
        if (modal.isDisplayed() && clean(modal.getText()).contains("Choose application country")) matches.add(modal);
      }
      if (matches.size() == 1) return matches.get(0);
      if (matches.size() > 1) throw new AssertionError("CA-29 expected one country chooser, found " + matches.size());
      sleep(100);
    }
    throw new AssertionError("CA-29 application type selection did not open country chooser");
  }

  private static List<SelenideElement> exactTypeRows(SelenideElement modal, String expected) {
    List<SelenideElement> result = new ArrayList<>();
    String wanted = clean(expected);
    for (SelenideElement label : modal.$$("label.form-check-label")) {
      if (label.isDisplayed() && wanted.equalsIgnoreCase(clean(label.getText()))) result.add(label);
    }
    if (!result.isEmpty()) return result;
    for (SelenideElement row : modal.$$(".modal-body .row")) {
      if (row.isDisplayed() && wanted.equalsIgnoreCase(clean(row.getText()))) result.add(row);
    }
    return result;
  }

  private static String modalInventory(SelenideElement modal) {
    List<String> result = new ArrayList<>();
    for (SelenideElement e : modal.$$("label.form-check-label,.modal-body .row")) {
      if (!e.isDisplayed()) continue;
      String text = clean(e.getText());
      if (!text.isBlank() && !result.contains(text)) result.add(text);
    }
    return result.toString();
  }

  /**
   * A chooser variant renders each option as a row with the click handler on
   * the exact-text cell, not on the row container. Resolve that cell before
   * invoking Selenide's download probe or the browser-click fallback.
   */
  private static SelenideElement exactTypeTarget(SelenideElement row, String expected) {
    List<SelenideElement> candidates = new ArrayList<>();
    String wanted = clean(expected);
    for (SelenideElement candidate : row.$$("label.form-check-label,.col,.col-auto,.col-1,div")) {
      if (candidate.isDisplayed() && wanted.equalsIgnoreCase(clean(candidate.getText()))) {
        candidates.add(candidate);
      }
    }
    return candidates.isEmpty() ? row : candidates.get(candidates.size() - 1);
  }

  private static SelenideElement exactCountryTarget(SelenideElement row) {
    var arrows = row.$$(".col-1.text-end");
    return arrows.isEmpty() ? exactTypeTarget(row, "Latvia") : arrows.get(arrows.size() - 1);
  }

  private static boolean isDirectDownloadTarget(SelenideElement element) {
    String tag = tagOf(element);
    return "A".equalsIgnoreCase(tag)
      && (!safeAttr(element, "href").isBlank() || !safeAttr(element, "download").isBlank());
  }

  private static void clickRendered(SelenideElement element) {
    executeJavaScript(
      "arguments[0].scrollIntoView({block:'center',inline:'center'}); arguments[0].click();",
      element.getWrappedElement());
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
          try { return Files.size(path) > 0 && Files.getLastModifiedTime(path).toMillis() >= minModified; }
          catch (Exception ignored) { return false; }
        })
        .max(Comparator.comparingLong(path -> {
          try { return Files.getLastModifiedTime(path).toMillis(); } catch (Exception ignored) { return 0L; }
        })).orElse(null);
    } catch (Exception ignored) { return null; }
  }

  private static void clearDirectory(Path directory) {
    try {
      Files.createDirectories(directory);
      try (var paths = Files.walk(directory)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) if (!path.equals(directory)) Files.deleteIfExists(path);
      }
    } catch (Exception error) {
      throw new AssertionError("CA-29 could not clear download directory " + directory, error);
    }
  }

  private static String clean(String value) {
    return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
  }

  private static String tagOf(SelenideElement element) {
    try { return element.getTagName(); } catch (Throwable ignored) { return "?"; }
  }

  private static String safeAttr(SelenideElement element, String name) {
    try { String v = element.getAttribute(name); return v == null ? "" : v; }
    catch (Throwable ignored) { return ""; }
  }

  private static String locatorSummary(SelenideElement element) {
    try {
      return "tag=" + tagOf(element)
        + " id=" + sanitizeDiagnosticText(safeAttr(element, "id"))
        + " class=" + sanitizeDiagnosticText(safeAttr(element, "class"))
        + " role=" + sanitizeDiagnosticText(safeAttr(element, "role"))
        + " type=" + sanitizeDiagnosticText(safeAttr(element, "type"))
        + " href_present=" + !safeAttr(element, "href").isBlank()
        + " download_present=" + !safeAttr(element, "download").isBlank();
    } catch (Throwable ignored) { return "unavailable"; }
  }

  /** Collect the installed CA-29 browser trace and persist it as evidence. */
  private static String captureCa29TraceEvidence(SelenideElement selected, Throwable directFailure) {
    try {
      Object trace = executeJavaScript("if (window.__ca29Trace && window.__ca29Trace.collect) window.__ca29Trace.collect(); return JSON.stringify(window.__ca29Trace || {});");
      String traceJson = sanitizeDiagnosticText(trace == null ? "{}" : trace.toString());
      JsonObject evidence = JsonParser.parseString(traceJson).getAsJsonObject();
      evidence.addProperty("observedForm", selected == null ? "" : sanitizeDiagnosticText(clean(selected.getText())));
      evidence.addProperty("selectedLocator", locatorSummary(selected));
      evidence.addProperty("directFailure", directFailure == null ? "none" : directFailure.getClass().getSimpleName());
      String msg = directFailure == null ? "" : directFailure.toString();
      String safeMessage = sanitizeDiagnosticText(msg == null ? "" : msg);
      evidence.addProperty("directFailureMessage", safeMessage.substring(0, Math.min(safeMessage.length(), 1600)));
      return AdminSteps.captureCa29Evidence(evidence.toString());
    } catch (Throwable error) {
      System.out.println("CA29_TRACE_CAPTURE_FAILED type=" + error.getClass().getSimpleName()
        + " msg=" + sanitizeDiagnosticText(error.getMessage()));
      return "";
    }
  }

  static String sanitizeDiagnosticText(String value) {
    if (value == null || value.isBlank()) return "";
    String safe = value;
    safe = replacePattern(safe, JSON_OUTER_HTML_PATTERN, match -> match.group(1) + "[REDACTED_OUTER_HTML]" + match.group(2));
    safe = replacePattern(safe, HTML_ATTRIBUTE_PATTERN, match -> match.group(1) + match.group(2)
      + "[REDACTED]" + match.group(2));
    safe = replacePattern(safe, JSON_ATTRIBUTE_PATTERN, match -> match.group(1) + "[REDACTED]" + match.group(3));
    safe = replacePattern(safe, QUERY_SECRET_PATTERN, match -> match.group(1) + "[REDACTED]");
    safe = replacePattern(safe, SENSITIVE_ASSIGNMENT_PATTERN, match -> {
      String matchedValue = match.group(2);
      String replacement = matchedValue.startsWith("\"") && matchedValue.endsWith("\"") ? "\"[REDACTED]\""
        : matchedValue.startsWith("'") && matchedValue.endsWith("'") ? "'[REDACTED]'" : "[REDACTED]";
      return match.group(1) + replacement;
    });
    safe = replacePattern(safe, BEARER_TOKEN_PATTERN, match -> "[REDACTED_TOKEN]");
    safe = replacePattern(safe, JWT_PATTERN, match -> "[REDACTED_TOKEN]");
    return replacePattern(safe, URL_PATTERN, match -> redactUrl(match.group()));
  }

  /**
   * Redact structured browser trace fields before they can be written to an
   * evidence file. Structural locator fields remain useful; raw markup and
   * URL-bearing or value-bearing attributes do not.
   */
  static String sanitizeDiagnosticTrace(String value) {
    String candidate = value == null || value.isBlank() ? "{}" : value;
    try {
      JsonElement root = JsonParser.parseString(candidate);
      redactJsonElement(root);
      return root.toString();
    } catch (RuntimeException ignored) {
      return sanitizeDiagnosticText(candidate);
    }
  }

  private static void redactJsonElement(JsonElement element) {
    if (element == null || element.isJsonNull()) return;
    if (element.isJsonArray()) {
      for (JsonElement child : element.getAsJsonArray()) redactJsonElement(child);
      return;
    }
    if (!element.isJsonObject()) return;

    JsonObject object = element.getAsJsonObject();
    for (String key : new ArrayList<>(object.keySet())) {
      JsonElement child = object.get(key);
      if ("outerHTML".equalsIgnoreCase(key)) {
        object.addProperty(key, "[REDACTED_OUTER_HTML]");
      } else if ("href".equalsIgnoreCase(key)
        || "download".equalsIgnoreCase(key)
        || "src".equalsIgnoreCase(key)
        || "action".equalsIgnoreCase(key)
        || "value".equalsIgnoreCase(key)) {
        object.addProperty(key, "[REDACTED]");
      } else if (child != null && child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
        object.addProperty(key, sanitizeDiagnosticText(child.getAsString()));
      } else {
        redactJsonElement(child);
      }
    }
  }

  private static String replacePattern(String value, Pattern pattern, java.util.function.Function<Matcher, String> replacement) {
    Matcher matcher = pattern.matcher(value);
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.apply(matcher)));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private static String redactUrl(String value) {
    if (value == null || value.isBlank()) return "";
    String candidate = value.trim();
    while (!candidate.isEmpty() && ".,;)]}\\".indexOf(candidate.charAt(candidate.length() - 1)) >= 0) {
      candidate = candidate.substring(0, candidate.length() - 1);
    }
    try {
      java.net.URI uri = java.net.URI.create(candidate);
      if (uri.getHost() == null) return "[REDACTED_URL]";
      StringBuilder safe = new StringBuilder(uri.getScheme()).append("://").append(uri.getHost());
      if (uri.getPort() >= 0) safe.append(':').append(uri.getPort());
      if (uri.getRawPath() != null) safe.append(uri.getRawPath());
      return safe.toString();
    } catch (IllegalArgumentException ignored) {
      int query = candidate.indexOf('?');
      int fragment = candidate.indexOf('#');
      int end = candidate.length();
      if (query >= 0) end = Math.min(end, query);
      if (fragment >= 0) end = Math.min(end, fragment);
      return candidate.substring(0, end);
    }
  }
}
