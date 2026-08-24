package runner;

import com.codeborne.selenide.WebDriverRunner;
import steps.BrowserBootstrapHooks;

import static com.codeborne.selenide.Selenide.closeWebDriver;
import static com.codeborne.selenide.Selenide.open;

/**
 * Starts only the browser/driver stack. It deliberately opens about:blank so
 * credentials, mTLS, network access, and application state cannot hide a
 * bootstrap failure.
 */
public final class BrowserSmoke {
  private BrowserSmoke() { }

  public static void main(String[] args) {
    BrowserBootstrapHooks.normalizeDriverBootstrap();
    try {
      open("about:blank");
      if (!WebDriverRunner.hasWebDriverStarted()) {
        throw new AssertionError("Browser smoke did not start a WebDriver");
      }
      System.out.println("BROWSER_SMOKE_OK url=" + WebDriverRunner.url());
    } finally {
      if (WebDriverRunner.hasWebDriverStarted()) closeWebDriver();
    }
  }
}
