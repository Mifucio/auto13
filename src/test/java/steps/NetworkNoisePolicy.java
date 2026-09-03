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
  private static final String ADMIN_GATEWAY_ANNOUNCEMENTS_PATH =
    "/services/gateway/api/internal-user-announcements";
  private static final String EXTERNAL_NOTIFICATIONS_PATH =
    "/services/external-users/api/notifications";
  private static final String EXTERNAL_EVENTS_PATH =
    "/services/external-users/api/events";
  private static final String EXTERNAL_ANNOUNCEMENTS_PATH =
    "/services/external-users/api/external-user-announcements";
  private static final String EXTERNAL_LOGIN_ANNOUNCEMENTS_PATH =
    "/services/external-users/api/external-login-announcements";
  private static final String APP_CONFIG_PATH = "/api/config";
  private static final String ADMIN_USERS_PATH =
    "/services/admin/sales-and-services-users";
  private static final Pattern DOKOBIT_AUTH_NAVIGATION =
    Pattern.compile("https://id-sandbox\\.dokobit\\.com/auth/[^?]+(?:\\?.*)?");
  private static final Pattern STATIC_I18N = Pattern.compile(".*/i18n/[^/?]+\\.json(?:\\?.*)?$");
  private static final Pattern DOKOBIT_STATIC_TRANSLATIONS =
    Pattern.compile("https://id-sandbox\\.dokobit\\.com/js/translations/translations\\.js(?:\\?.*)?");

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
          || url.contains(ADMIN_GATEWAY_ANNOUNCEMENTS_PATH)
          || url.contains(EXTERNAL_NOTIFICATIONS_PATH)
          || url.contains(EXTERNAL_EVENTS_PATH)
          || url.contains(EXTERNAL_ANNOUNCEMENTS_PATH)
          || url.contains(EXTERNAL_LOGIN_ANNOUNCEMENTS_PATH)
          || url.contains(APP_CONFIG_PATH)
          || url.contains(ADMIN_USERS_PATH)
          || DOKOBIT_AUTH_NAVIGATION.matcher(url).matches()
          || STATIC_I18N.matcher(url).matches()
          || DOKOBIT_STATIC_TRANSLATIONS.matcher(url).matches()) {
        iterator.remove();
        removed = true;
      }
    }
    if (removed && PENDING_DATA_REQUESTS.isEmpty()) lastDataActivityAt = 0;
  }
}
