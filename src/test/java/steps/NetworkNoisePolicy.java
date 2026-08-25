package steps;

import java.util.Iterator;
import java.util.Map;

import static steps.RuntimeState.PENDING_DATA_REQUESTS;
import static steps.RuntimeState.lastDataActivityAt;

/**
 * Classifies observed background requests that must not decide business-step
 * completion. The live run showed GetTranslations remaining pending for more
 * than 15 seconds while the page was otherwise usable; treating that bootstrap
 * request as business data produced false HANG_DETECTED failures.
 */
final class NetworkNoisePolicy {
  private static final String TRANSLATIONS_PATH = "/services/holdersinformation/translation/GetTranslations";

  private NetworkNoisePolicy() { }

  static void discardNonBlockingBackgroundRequests() {
    boolean removed = false;
    Iterator<Map.Entry<String, RuntimeState.PendingRequest>> iterator = PENDING_DATA_REQUESTS.entrySet().iterator();
    while (iterator.hasNext()) {
      RuntimeState.PendingRequest pending = iterator.next().getValue();
      String url = pending == null ? "" : pending.url;
      if (url != null && url.contains(TRANSLATIONS_PATH)) {
        iterator.remove();
        removed = true;
      }
    }
    if (removed && PENDING_DATA_REQUESTS.isEmpty()) {
      lastDataActivityAt = 0;
    }
  }
}
