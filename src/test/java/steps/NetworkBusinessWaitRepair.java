package steps;

import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

import static steps.RuntimeState.*;

/**
 * Business-data wait with one live-proven admin SPA exception: after navigation
 * from the Submitted list to an exact numeric application detail, Chrome can
 * retain the superseded internal-user-applications list request in performance
 * logs even though the target detail is fully rendered. The same endpoint stays
 * blocking while the browser is actually on the list.
 */
final class NetworkBusinessWaitRepair {
  private static final String ADMIN_LIST_ENDPOINT =
    "/services/corporate-actions/api/internal-user-applications";

  private NetworkBusinessWaitRepair() { }

  static void waitForBusinessData() {
    long startedAt = System.currentTimeMillis();
    while (true) {
      NetworkMockSupport.drainPerformanceLogs();
      NetworkNoisePolicy.discardNonBlockingBackgroundRequests();
      discardSupersededAdminListRequestOnDetail();

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

  private static void discardSupersededAdminListRequestOnDetail() {
    if (!com.codeborne.selenide.WebDriverRunner.hasWebDriverStarted()) return;
    String current = com.codeborne.selenide.WebDriverRunner.url();
    if (current == null
        || !current.matches(".*/corporate-actions/application-form/\\d+(?:[/?#].*)?")) return;

    boolean removed = false;
    Iterator<Map.Entry<String, RuntimeState.PendingRequest>> iterator =
      PENDING_DATA_REQUESTS.entrySet().iterator();
    while (iterator.hasNext()) {
      RuntimeState.PendingRequest pending = iterator.next().getValue();
      if (pending != null && pending.url != null && pending.url.contains(ADMIN_LIST_ENDPOINT)) {
        iterator.remove();
        removed = true;
      }
    }
    if (removed && PENDING_DATA_REQUESTS.isEmpty()) lastDataActivityAt = 0;
  }
}
