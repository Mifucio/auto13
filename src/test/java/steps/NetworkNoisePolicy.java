package steps;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import static steps.RuntimeState.PENDING_DATA_REQUESTS;
import static steps.RuntimeState.lastDataActivityAt;

/** Classifies UI-bootstrap requests that must not decide business-step completion. */
final class NetworkNoisePolicy {
  private static final String TRANSLATIONS_PATH = "/services/holdersinformation/translation/GetTranslations";
  private static final String ADMIN_ANNOUNCEMENTS = "/services/gateway/api/internal-user-announcements";
  private static final String ADMIN_SALES_USERS = "/services/gateway/api/admin/sales-and-services-users";
  private static final Pattern STATIC_I18N = Pattern.compile(".*/i18n/[^/?]+\\.json(?:\\?.*)?$");

  private NetworkNoisePolicy() { }

  static void discardNonBlockingBackgroundRequests() {
    boolean removed = false;
    Iterator<Map.Entry<String, RuntimeState.PendingRequest>> iterator = PENDING_DATA_REQUESTS.entrySet().iterator();
    while (iterator.hasNext()) {
      RuntimeState.PendingRequest pending = iterator.next().getValue();
      String url = pending == null || pending.url == null ? "" : pending.url;
      boolean shellBootstrap = url.contains(ADMIN_ANNOUNCEMENTS) || url.contains(ADMIN_SALES_USERS);
      if (url.contains(TRANSLATIONS_PATH) || STATIC_I18N.matcher(url).matches() || shellBootstrap) {
        iterator.remove();
        removed = true;
      }
    }
    if (removed && PENDING_DATA_REQUESTS.isEmpty()) lastDataActivityAt = 0;
  }
}
