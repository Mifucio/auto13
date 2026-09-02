package steps;

import com.codeborne.selenide.WebDriverRunner;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * TEMPORARY — saves and restores browser cookies to skip re-authentication
 * for scenarios whose login is a precondition, not the test target.
 *
 * Cookies are persisted to {@code build/.auth-cookies.json}. The save happens
 * after a login-test scenario (Dokobit, manual) passes. The restore happens
 * before precondition scenarios (company selection, settings, etc.).
 *
 * Remove alongside TemporaryPerfAnalyzer once all scenarios are accepted.
 */
public final class CookieSessionManager {

  private static final Path COOKIE_FILE = Path.of("build", ".auth-cookies.json");
  private static final Gson GSON = new GsonBuilder().create();
  private static final Type COOKIE_LIST_TYPE = new TypeToken<List<SerializableCookie>>() {}.getType();

  /** Max cookie age before re-authentication is forced (50 minutes). */
  private static final long MAX_AGE_MS = 50 * 60 * 1000L;
  /** Track whether we've ever saved cookies in this run. */
  private static boolean everSaved = false;

  private CookieSessionManager() { }

  /** True when a previously saved cookie session is available. */
  public static boolean hasSavedSession() {
    return Files.isRegularFile(COOKIE_FILE) && COOKIE_FILE.toFile().length() > 10;
  }

  /** True when cookies are available and not expired. */
  public static boolean hasFreshSession() {
    if (!hasSavedSession()) return false;
    try {
      long ageMs = System.currentTimeMillis() - Files.getLastModifiedTime(COOKIE_FILE).toMillis();
      return ageMs < MAX_AGE_MS;
    } catch (Exception e) {
      return false;
    }
  }

  /** True if we've ever saved cookies in this run (used to skip login re-runs). */
  public static boolean hasEverSavedInThisRun() {
    return everSaved;
  }

  /**
   * Save all current browser cookies to the cookie file.
   * Call this after a login-test scenario (Dokobit/manual) passes.
   */
  public static void saveCookies() {
    if (!WebDriverRunner.hasWebDriverStarted()) {
      System.out.println("[cookies] no browser to save cookies from");
      return;
    }
    try {
      WebDriver driver = WebDriverRunner.getWebDriver();
      Set<Cookie> cookies = driver.manage().getCookies();
      if (cookies.isEmpty()) {
        System.out.println("[cookies] no cookies to save (empty set)");
        return;
      }
      List<SerializableCookie> serializable = new ArrayList<>();
      for (Cookie c : cookies) {
        serializable.add(new SerializableCookie(c));
      }
      Files.createDirectories(COOKIE_FILE.toAbsolutePath().getParent());
      Files.writeString(COOKIE_FILE, GSON.toJson(serializable), StandardCharsets.UTF_8);
      everSaved = true;
      System.out.printf("[cookies] saved %d cookies to %s%n", serializable.size(), COOKIE_FILE.toAbsolutePath());
    } catch (Exception e) {
      System.err.println("[cookies] save failed: " + e);
    }
  }

  /**
   * Restore saved cookies into the current browser session.
   * Must be called while the browser is on the target domain (navigate there first).
   */
  public static void restoreCookies() {
    if (!WebDriverRunner.hasWebDriverStarted()) {
      System.out.println("[cookies] no browser to restore cookies into");
      return;
    }
    if (!hasSavedSession()) {
      System.out.println("[cookies] no saved session to restore");
      return;
    }
    try {
      WebDriver driver = WebDriverRunner.getWebDriver();
      String json = Files.readString(COOKIE_FILE, StandardCharsets.UTF_8);
      List<SerializableCookie> deserialized = GSON.fromJson(json, COOKIE_LIST_TYPE);
      if (deserialized == null || deserialized.isEmpty()) {
        System.out.println("[cookies] saved cookie file is empty");
        return;
      }

      // Delete existing cookies first, then inject saved ones.
      driver.manage().deleteAllCookies();

      // Navigate to the base URL so the browser is on the right origin for
      // cookie injection (must match the cookie's domain).
      String host = java.net.URI.create(driver.getCurrentUrl()).getHost();

      int restored = 0;
      int skipped = 0;
      for (SerializableCookie sc : deserialized) {
        if (sc.value == null || sc.value.isBlank()) {
          skipped++;
          continue;
        }
        // Skip cookies whose domain doesn't match the current host.
        String cd = sc.domain;
        if (cd != null && !cd.isBlank() && host != null && !host.equals(cd) && !host.endsWith("." + cd) && !cd.equals("." + host)) {
          skipped++;
          continue;
        }
        try {
          Cookie cookie = sc.toCookie();
          driver.manage().addCookie(cookie);
          restored++;
        } catch (Exception e) {
          System.out.printf("[cookies] skipped cookie '%s' (domain='%s'): %s%n",
              sc.name, sc.domain, e.getMessage());
          skipped++;
        }
      }
      System.out.printf("[cookies] restored %d/%d cookies (skipped %d) from %s%n",
          restored, deserialized.size(), skipped, COOKIE_FILE.toAbsolutePath());
    } catch (Exception e) {
      System.err.println("[cookies] restore failed: " + e);
    }
  }

  /** Delete the saved cookie file. */
  public static void clearSavedSession() {
    try {
      if (Files.isRegularFile(COOKIE_FILE)) {
        Files.delete(COOKIE_FILE);
        System.out.println("[cookies] cleared saved session");
      }
    } catch (Exception e) {
      System.err.println("[cookies] clear failed: " + e);
    }
  }

  // ── Serializable cookie DTO ───────────────────────────────────

  @SuppressWarnings("unused")
  private static final class SerializableCookie {
    String name;
    String value;
    String domain;
    String path;
    long expiry;
    boolean secure;
    boolean httpOnly;
    String sameSite;

    SerializableCookie() { }

    SerializableCookie(Cookie c) {
      this.name = c.getName();
      this.value = c.getValue();
      this.domain = c.getDomain() != null ? c.getDomain() : "";
      this.path = c.getPath() != null ? c.getPath() : "/";
      this.expiry = c.getExpiry() != null ? c.getExpiry().getTime() : -1;
      this.secure = c.isSecure();
      this.httpOnly = c.isHttpOnly();
      this.sameSite = c.getSameSite();
    }

    Cookie toCookie() {
      Cookie.Builder builder = new Cookie.Builder(name, value)
          .path(path)
          .isSecure(true)   // Force secure=true (SameSite=None requires it)
          .isHttpOnly(httpOnly);
      // Selenium's addCookie rejects domain that doesn't match the current
      // page origin. Omit the domain so it defaults to the current host.
      if (sameSite != null && !sameSite.isBlank()) {
        builder.sameSite(sameSite);
      }
      if (expiry > 0) {
        builder.expiresOn(new Date(expiry));
      }
      return builder.build();
    }
  }
}