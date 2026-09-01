package steps;

import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.WebDriverRunner;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.sleep;
import static steps.NetworkMockSupport.*;

/**
 * Steps for mocking expired / timed-out signature scenarios on disposable
 * Corporate Actions applications.
 *
 * These steps intercept backend API calls to simulate a Dokobit signing
 * session that has expired, and capture the application's error message.
 *
 * TEMPORARY — created for manual exploratory testing. Remove once the
 * expired-signing error flow is verified.
 */
public final class MockExpiredSignatureSteps {

  private static String capturedErrorMessage = "";

  @When("I mock the Dokobit signing API to return expired session")
  public void mockDokobitSigningApiExpired() {
    // Intercept POST requests to the SETS domain containing "sign" in the path.
    // Use a proper URL pattern so Firefox BiDi can parse it: the pattern
    // "https://eservicesdev.sets.lv/*sign*" becomes
    // "https://eservicesdev.sets.lv/\*sign\*" (valid URL with wildcards).
    // This avoids intercepting Dokobit gateway (gateway-sandbox.dokobit.com).
    try {
      mockHttpStatus("https://eservicesdev.sets.lv/*sign*", 401);
      System.out.println("  🔀 Mocked SETS signing endpoints → HTTP 401 (expired session)");
    } catch (Exception e) {
      System.out.println("  ⚠️  Signing mock unavailable: " + e.getMessage());
      System.out.println("  ℹ️  Will observe app behavior without mock");
    }
  }

  @Then("an expired signing error message is displayed")
  public void expiredSigningErrorMessageDisplayed() {
    // Wait for the page to update after the Sign button click attempt.
    // The app may show an error toast, a message in the body, or nothing.
    long deadline = System.currentTimeMillis() + 30000;
    String lastBody = "";
    while (System.currentTimeMillis() < deadline) {
      try {
        lastBody = $("body").shouldBe(visible).getText();
      } catch (Exception e) {
        continue;
      }
      if (lastBody != null && lastBody.trim().length() > 0) {
        // Check for any alerts/toasts
        String alertText = "";
        for (SelenideElement alert : $$(".alert-danger, .alert-warning, .alert-info, "
            + ".toast-error, .toast-warning, [role=alert], .alert")) {
          if (alert.isDisplayed()) {
            alertText = alert.getText().trim();
            break;
          }
        }
        if (!alertText.isEmpty()) {
          System.out.println("  ✅ Error alert found: \"" + alertText + "\"");
          capturedErrorMessage = alertText;
          return;
        }
        // Also check if the page changed state
        if (lastBody.toLowerCase(java.util.Locale.ROOT).contains("error")
            || lastBody.toLowerCase(java.util.Locale.ROOT).contains("failed")
            || lastBody.toLowerCase(java.util.Locale.ROOT).contains("expired")
            || lastBody.toLowerCase(java.util.Locale.ROOT).contains("timeout")
            || lastBody.toLowerCase(java.util.Locale.ROOT).contains("cancel")) {
          System.out.println("  ✅ Error keyword found in page body");
          capturedErrorMessage = extractErrorMessage(lastBody);
          return;
        }
      }
      sleep(500);
    }
    System.out.println("  📄 Page body at end of wait:");
    System.out.println("  " + (lastBody != null ? lastBody.substring(0, Math.min(lastBody.length(), 3000)) : "(empty)"));
    // Also log all alerts visible on the page
    System.out.println("  📋 Visible alerts on page:");
    for (SelenideElement el : $$("[class*=alert], [class*=toast], [class*=message], [role=alert], [role=status]")) {
      try {
        if (el.isDisplayed()) {
          System.out.println("    - [" + el.getTagName() + "] '" + el.getText().trim() + "'");
        }
      } catch (Throwable ignored) {}
    }
    capturedErrorMessage = lastBody != null ? lastBody : "(empty page)";
  }

  @And("I capture the expiration error message text")
  public void captureExpirationErrorMessageText() {
    // Pause 5 seconds so the user can visually inspect the error message
    // in the headed browser before we print it.
    sleep(5000);

    if (capturedErrorMessage.isEmpty()) {
      try {
        capturedErrorMessage = $("body").shouldBe(visible).getText();
      } catch (Exception e) {
        capturedErrorMessage = "(could not read page body)";
      }
    }
    System.out.println("\n═══════════════════════════════════════════");
    System.out.println("  📋  EXPIRED SIGNATURE ERROR MESSAGE:");
    System.out.println("───────────────────────────────────────────");
    // Look for alerts, toasts, or error containers
    for (java.util.regex.Pattern p : new java.util.regex.Pattern[]{
        java.util.regex.Pattern.compile("(?i)(error[^.]*\\.)"),
        java.util.regex.Pattern.compile("(?i)(expired[^.]*\\.)"),
        java.util.regex.Pattern.compile("(?i)(timeout[^.]*\\.)"),
        java.util.regex.Pattern.compile("(?i)(failed[^.]*\\.)"),
        java.util.regex.Pattern.compile("(?i)(unavailable[^.]*\\.)"),
        java.util.regex.Pattern.compile("(?i)(signature process has not[^.]*\\.)")
    }) {
      java.util.regex.Matcher m = p.matcher(capturedErrorMessage);
      while (m.find()) {
        System.out.println("  📌 " + m.group(1).trim());
      }
    }
    System.out.println("───────────────────────────────────────────");
    System.out.println("  Raw snippet: " + capturedErrorMessage.substring(0,
      Math.min(capturedErrorMessage.length(), 1000)));
    System.out.println("═══════════════════════════════════════════\n");
  }

  /**
   * Extract the most relevant error message from the page body.
   */
  private static String extractErrorMessage(String body) {
    if (body == null || body.isBlank()) return "(empty page)";

    // Look for Angular alert messages (common in this app)
    for (SelenideElement alert : $$(".alert-danger, .alert-warning, .alert-info, "
        + ".toast-error, .toast-warning, [role=alert]")) {
      if (alert.isDisplayed()) {
        String text = alert.getText().trim();
        if (!text.isEmpty()) return text;
      }
    }

    // Look for error containers
    for (SelenideElement errorEl : $$("[class*=error], [class*=alert], [class*=toast]")) {
      if (errorEl.isDisplayed()) {
        String text = errorEl.getText().trim();
        if (!text.isEmpty() && text.length() < 500) return text;
      }
    }

    // Fallback: find the first sentence mentioning error/expired/failed
    for (String line : body.split("\n")) {
      String trimmed = line.trim();
      String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
      if (lower.contains("error") || lower.contains("expired") || lower.contains("failed")
          || lower.contains("timeout") || lower.contains("unavailable")) {
        return trimmed.substring(0, Math.min(trimmed.length(), 300));
      }
    }

    return body.substring(0, Math.min(body.length(), 500));
  }
}