package steps;

import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

import static steps.RuntimeState.*;

/**
 * Keeps UI translation/bootstrap traffic out of the business-data hang gate.
 * These URLs were observed remaining pending while the rendered page was fully
 * usable. Business XHR/fetch requests remain fail-closed and timed.
 */
final class NetworkNoisePolicy {
  private static final String TRANSLATIONS_PATH = "/services/holdersinformation/translation/GetTranslations";
  private static final String I18N_PATH = "/i18n/";

  private NetworkNoisePolicy() { }

  static void waitForBusinessData() {
    long startedAt = System.currentTimeMillis();
    while (true) {
      NetworkMockSupport.drainPerformanceLogs();
      discardNonBlockingBackgroundRequests();
      long now = System.currentTimeMillis();
      boolean quiet = PENDING_DATA_REQUESTS.isEmpty()
        && (lastDataActivityAt == 0 || now - lastDataActivityAt >= NETWORK_QUIET_MS);
      if (quiet) return;
      if (now - startedAt >= Math.min(EXTERNAL_TIMEOUT_MS, HANG_TIMEOUT_MS)) {
        String pendingUrls = PENDING_DATA_REQUESTS.values().stream()
          .map(request -> request.url).distinct().collect(Collectors.joining(", "));
        throw new AssertionError("HANG_DETECTED external business data exceeded " + EXTERNAL_TIMEOUT_MS
          + "ms; pending URLs: " + pendingUrls);
      }
      try {
        Thread.sleep(50);
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw new AssertionError("HANG_DETECTED external business data wait interrupted", error);
      }
    }
  }

  static void discardNonBlockingBackgroundRequests() {
    boolean removed = false;
    Iterator<Map.Entry<String, RuntimeState.PendingRequest>> iterator = PENDING_DATA_REQUESTS.entrySet().iterator();
    while (iterator.hasNext()) {
      RuntimeState.PendingRequest pending = iterator.next().getValue();
      String url = pending == null ? "" : pending.url;
      if (isNonBlocking(url)) {
        iterator.remove();
        removed = true;
      }
    }
    if (removed && PENDING_DATA_REQUESTS.isEmpty()) lastDataActivityAt = 0;
  }

  private static boolean isNonBlocking(String url) {
    if (url == null || url.isBlank()) return false;
    if (url.contains(TRANSLATIONS_PATH)) return true;
    int i18n = url.indexOf(I18N_PATH);
    if (i18n < 0) return false;
    String tail = url.substring(i18n + I18N_PATH.length());
    int query = tail.indexOf('?');
    if (query >= 0) tail = tail.substring(0, query);
    return tail.toLowerCase(java.util.Locale.ROOT).endsWith(".json");
  }
}
