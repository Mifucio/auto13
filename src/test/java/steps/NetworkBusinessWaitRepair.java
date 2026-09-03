package steps;

import java.util.Iterator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
  private static final long ABANDONED_REQUEST_GRACE_MS = 5000;
  private static final String ADMIN_LIST_ENDPOINT =
    "/services/corporate-actions/api/internal-user-applications";
  private static final String EXTERNAL_LIST_ENDPOINT =
    "/services/corporate-actions/api/external-user-applications";
  private static final String DOKOBIT_SESSION_ENDPOINT =
    "/services/external-users/api/dokobit/sessionToken";
  private static final String REPRESENTABLE_ENTITIES_ENDPOINT =
    "/services/external-users/api/representable-entities";
  private static final String EXTERNAL_MANAGEMENT_INFO_ENDPOINT =
    "/services/external-users/management/info";
  private static final Set<String> CONSUMED_ABANDONED_ENDPOINTS = new HashSet<>();

  private NetworkBusinessWaitRepair() { }

  static void waitForBusinessData() {
    long startedAt = System.currentTimeMillis();
    while (true) {
      NetworkMockSupport.drainPerformanceLogs();
      NetworkNoisePolicy.discardNonBlockingBackgroundRequests();
      discardRequestsWithCompletedResponseBodies();
      discardSupersededCustomerBootstrapRequests();
      discardSupersededAdminListRequestOnDetail();
      discardSupersededExternalListRequestOnDetail();

      long now = System.currentTimeMillis();
      boolean quiet = PENDING_DATA_REQUESTS.isEmpty()
        && (lastDataActivityAt == 0 || now - lastDataActivityAt >= NETWORK_QUIET_MS);
      if (quiet) return;

      // EXTERNAL_TIMEOUT_MS is the performance budget reported for slow data.
      // Treating that lower budget as the hang deadline made healthy but busy
      // live environments fail after 15 seconds even though the configured
      // hang guard is 45 seconds.
      if (now - startedAt >= HANG_TIMEOUT_MS) {
        String pendingUrls = PENDING_DATA_REQUESTS.values().stream()
          .map(request -> sanitizedPath(request.url)).distinct().collect(Collectors.joining(", "));
        String pendingState = PENDING_DATA_REQUESTS.entrySet().stream()
          .map(entry -> pendingState(entry.getKey(), entry.getValue()))
          .distinct().collect(Collectors.joining(", "));
        throw new AssertionError("HANG_DETECTED external business data exceeded " + HANG_TIMEOUT_MS
          + "ms; pending paths: " + pendingUrls + "; state: " + pendingState);
      }

      try {
        Thread.sleep(50);
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw new AssertionError("HANG_DETECTED external business data wait interrupted", error);
      }
    }
  }

  /** Chrome can omit loadingFinished even after an exact request body is ready. */
  private static void discardRequestsWithCompletedResponseBodies() {
    if (PENDING_DATA_REQUESTS.isEmpty()
        || !com.codeborne.selenide.WebDriverRunner.hasWebDriverStarted()) return;
    boolean removed = false;
    for (Map.Entry<String, RuntimeState.PendingRequest> entry : PENDING_DATA_REQUESTS.entrySet()) {
      RuntimeState.PendingRequest pending = entry.getValue();
      if (pending == null || pending.url == null) continue;
      boolean successfulResponse = pending.responseStatus >= 200 && pending.responseStatus < 300
        && pending.responseReceivedAt >= pending.startedAt;
      if (successfulResponse && NetworkMockSupport.hasCompletedResponseBody(entry.getKey())
          && PENDING_DATA_REQUESTS.remove(entry.getKey(), pending)) {
        removed = true;
      }
    }
    if (removed && PENDING_DATA_REQUESTS.isEmpty()) lastDataActivityAt = 0;
  }

  private static String sanitizedPath(String rawUrl) {
    if (rawUrl == null || rawUrl.isBlank()) return "unknown";
    try {
      String path = java.net.URI.create(rawUrl).getPath();
      if (path == null || path.isBlank()) return "unknown";
      return path.replaceAll("/\\d+(?=/|$)", "/[id]");
    } catch (RuntimeException ignored) {
      return "unknown";
    }
  }

  private static String pendingState(String requestId, RuntimeState.PendingRequest pending) {
    if (pending == null) return "unknown";
    boolean responseSeen = pending.responseReceivedAt >= pending.startedAt && pending.responseStatus > 0;
    boolean successful = pending.responseStatus >= 200 && pending.responseStatus < 300;
    boolean bodyReady = responseSeen && NetworkMockSupport.hasCompletedResponseBody(requestId);
    return "{path=" + sanitizedPath(pending.url)
      + ",method=" + (pending.method == null || pending.method.isBlank() ? "unknown" : pending.method)
      + ",responseSeen=" + responseSeen + ",successful=" + successful + ",bodyReady=" + bodyReady + "}";
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

  /**
   * The authentication SPA occasionally omits loadingFailed for requests that
   * its successful route transition aborts. Do not waive those requests while
   * login or company selection is still active. Once a same-origin customer
   * route and its rendered shell are both present, the abandoned bootstrap
   * request can no longer decide the completed login step.
   */
  private static void discardSupersededCustomerBootstrapRequests() {
    if (!com.codeborne.selenide.WebDriverRunner.hasWebDriverStarted()) return;
    String current = com.codeborne.selenide.WebDriverRunner.url();
    if (!isRenderedAuthenticatedCustomerRoute(current)) return;

    long evidenceAt = System.currentTimeMillis();
    discardOldestAbandoned(DOKOBIT_SESSION_ENDPOINT, "POST", evidenceAt);
    discardOldestAbandoned(REPRESENTABLE_ENTITIES_ENDPOINT, "GET", evidenceAt);
    discardOldestAbandoned(EXTERNAL_MANAGEMENT_INFO_ENDPOINT, "GET", evidenceAt);
  }

  private static boolean isRenderedAuthenticatedCustomerRoute(String current) {
    if (!AuthSupport.sameOrigin(current, BASE_URL)) return false;
    try {
      String path = java.net.URI.create(current).getPath();
      if (path == null) return false;
      String normalized = path.toLowerCase(java.util.Locale.ROOT);
      if (normalized.contains("/error") || normalized.contains("/access-denied")) return false;
      if (normalized.contains("/company-selection")) {
        Object cardsRendered = com.codeborne.selenide.Selenide.executeJavaScript(
          "return [...document.querySelectorAll('a.stretched-link')]"
            + ".some(card=>card.offsetParent!==null&&card.getClientRects().length>0);");
        return Boolean.TRUE.equals(cardsRendered);
      }
      if (normalized.contains("/login")) return false;
      Object rendered = com.codeborne.selenide.Selenide.executeJavaScript(
        "const represented=document.querySelector('#navbarRepresentedDropdown');"
          + "return (document.readyState==='interactive'||document.readyState==='complete')"
          + "&&!!represented&&represented.offsetParent!==null"
          + "&&String(represented.innerText||'').trim().length>0;");
      return Boolean.TRUE.equals(rendered);
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private static void discardSupersededExternalListRequestOnDetail() {
    if (!com.codeborne.selenide.WebDriverRunner.hasWebDriverStarted()) return;
    String current = com.codeborne.selenide.WebDriverRunner.url();
    if (current == null
        || !current.matches(".*/corporate-actions/application-form/\\d+(?:[/?#].*)?")) return;
    Object rendered = com.codeborne.selenide.Selenide.executeJavaScript(
      "return !!document.querySelector('form,button,a,[role=button]');");
    if (!Boolean.TRUE.equals(rendered)) return;
    discardOldestAbandoned(EXTERNAL_LIST_ENDPOINT, "GET", System.currentTimeMillis());
  }

  static void resetRenderedEvidence() {
    CONSUMED_ABANDONED_ENDPOINTS.clear();
  }

  /** Remove one exact request ID only after it has remained response-less past
   * the grace period and predates the rendered postcondition that supersedes it. */
  private static boolean discardOldestAbandoned(String endpoint, String method, long evidenceAt) {
    if (CONSUMED_ABANDONED_ENDPOINTS.contains(endpoint)) return false;
    long now = System.currentTimeMillis();
    Map.Entry<String, RuntimeState.PendingRequest> candidate = null;
    for (Map.Entry<String, RuntimeState.PendingRequest> entry : PENDING_DATA_REQUESTS.entrySet()) {
      RuntimeState.PendingRequest pending = entry.getValue();
      if (pending == null || pending.url == null || !pending.url.contains(endpoint)) continue;
      if (!method.equalsIgnoreCase(pending.method) || pending.responseStatus > 0) continue;
      if (pending.startedAt > evidenceAt || now - pending.startedAt < ABANDONED_REQUEST_GRACE_MS) continue;
      if (candidate == null || pending.startedAt < candidate.getValue().startedAt) candidate = entry;
    }
    if (candidate == null) return false;
    boolean removed = PENDING_DATA_REQUESTS.remove(candidate.getKey(), candidate.getValue());
    if (removed) CONSUMED_ABANDONED_ENDPOINTS.add(endpoint);
    if (removed && PENDING_DATA_REQUESTS.isEmpty()) lastDataActivityAt = 0;
    return removed;
  }

}
