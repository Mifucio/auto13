package steps;

import com.codeborne.selenide.SelenideElement;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.sleep;
import static com.codeborne.selenide.WebDriverRunner.url;

/**
 * Reuses one saved draft per form for read-only History/Attachments scenarios.
 * If a cached draft disappears or its session is stale, the step falls back to
 * the normal fresh prerequisite and refreshes the cache. This removes duplicate
 * create/save work without making the suite order-dependent.
 */
public final class ReusableDisposableDraftSteps {
  private static final Pattern APPLICATION_ID =
    Pattern.compile("/corporate-actions/application-form/(\\d+)(?:[/?#].*)?$");
  private static final Map<String, String> READ_ONLY_DRAFT_IDS = new ConcurrentHashMap<>();

  private final DisposableScenarioPrerequisites prerequisites;

  public ReusableDisposableDraftSteps(DisposableScenarioPrerequisites prerequisites) {
    this.prerequisites = prerequisites;
  }

  @And("I remember this disposable {string} draft for read-only scenarios")
  public void rememberCurrentDraft(String type) {
    String id = applicationId(url());
    if (id == null) {
      throw new AssertionError("Cannot cache disposable " + type + " draft without numeric application id; url=" + url());
    }
    READ_ONLY_DRAFT_IDS.put(key(type), id);
    System.out.println("DISPOSABLE_DRAFT_CACHED type=" + type + " id=" + id);
  }

  @Given("a reusable saved disposable {string} application exists")
  public void reusableSavedDraft(String type) throws Exception {
    String cacheKey = key(type);
    String id = READ_ONLY_DRAFT_IDS.get(cacheKey);

    if (id != null) {
      try {
        prerequisites.prepareReusableCustomerContext();
        open("/corporate-actions/application-form/" + id);
        if (awaitReusableDraft(type, id, 8000)) {
          System.out.println("DISPOSABLE_DRAFT_REUSED type=" + type + " id=" + id);
          return;
        }
      } catch (Throwable ignored) {
        // A stale/deleted cached draft is not a test failure; create a fresh one.
      }
      READ_ONLY_DRAFT_IDS.remove(cacheKey, id);
    }

    prerequisites.freshSavedDisposableApplication(type);
    String freshId = applicationId(url());
    if (freshId == null) {
      throw new AssertionError("Fresh reusable disposable " + type + " draft exposed no numeric id; url=" + url());
    }
    READ_ONLY_DRAFT_IDS.put(cacheKey, freshId);
    System.out.println("DISPOSABLE_DRAFT_CACHE_REFRESH type=" + type + " id=" + freshId);
  }

  private static boolean awaitReusableDraft(String type, String id, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    String wanted = type.toLowerCase(Locale.ROOT);
    while (System.currentTimeMillis() < deadline) {
      try {
        String current = url();
        if (current != null && current.contains("/corporate-actions/application-form/" + id)) {
          SelenideElement body = $("body");
          String text = body.getText();
          String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
          if (normalized.contains(wanted)
              && (normalized.contains("sign document") || normalized.contains("draft"))) {
            return true;
          }
        }
      } catch (Throwable ignored) { }
      sleep(100);
    }
    return false;
  }

  private static String applicationId(String current) {
    if (current == null) return null;
    Matcher matcher = APPLICATION_ID.matcher(current);
    return matcher.find() ? matcher.group(1) : null;
  }

  private static String key(String type) {
    return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
  }
}
