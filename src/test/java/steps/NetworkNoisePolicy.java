package steps;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import static steps.RuntimeState.PENDING_DATA_REQUESTS;
import static steps.RuntimeState.lastDataActivityAt;

/** Classifies UI-bootstrap/polling requests that must not decide business-step completion. */
final class NetworkNoisePolicy {
  private static final String TRANSLATIONS_PATH = "/services/holdersinformation/translation/GetTranslations";
  private static final String DISCLOSURE_POLL_PATH =
    "/services/holdersinformation/message-hub/GetShareholderDisclosureRequestsForMe";
  private static final String ADMIN_ANNOUNCEMENTS_PATH =
    "/services/corporate-actions/api/internal-user-announcements";
  private static final String ADMIN_USERS_PATH =
    "/services/admin/sales-and-services-users";
  // The admin list's CSD-user assignment is persisted asynchronously. It is
  // not a prerequisite for the customer attachment flow, and the live service
  // can leave this request pending after the assignment has been applied.
  private static final String ADMIN_CSD_USER_UPDATE_PATH =
    "/services/gateway/api/application/update-csd-user";
  private static final Pattern DOKOBIT_AUTH_NAVIGATION =
    Pattern.compile("https://id-sandbox\\.dokobit\\.com/auth/[^?]+(?:\\?.*)?");
  private static final Pattern STATIC_I18N = Pattern.compile(".*/i18n/[^/?]+\\.json(?:\\?.*)?$");

  private NetworkNoisePolicy() { }

  static void discardNonBlockingBackgroundRequests() {
    boolean removed = false;
    Iterator<Map.Entry<String, RuntimeState.PendingRequest>> iterator = PENDING_DATA_REQUESTS.entrySet().iterator();
    while (iterator.hasNext()) {
      RuntimeState.PendingRequest pending = iterator.next().getValue();
      String url = pending == null || pending.url == null ? "" : pending.url;
      if (url.contains(TRANSLATIONS_PATH)
          || url.contains(DISCLOSURE_POLL_PATH)
          || url.contains(ADMIN_ANNOUNCEMENTS_PATH)
          || url.contains(ADMIN_USERS_PATH)
          || url.contains(ADMIN_CSD_USER_UPDATE_PATH)
          || DOKOBIT_AUTH_NAVIGATION.matcher(url).matches()
          || STATIC_I18N.matcher(url).matches()) {
        iterator.remove();
        removed = true;
      }
    }
    if (removed && PENDING_DATA_REQUESTS.isEmpty()) lastDataActivityAt = 0;
  }
}
