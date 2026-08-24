package steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CA-26-specific safety boundary.
 *
 * This class deliberately validates only an explicitly approved disposable
 * contract. It never reads credential files, logs contract values, opens a
 * browser, calls Dokobit, or confirms a signature. A future live adapter must
 * be introduced behind the same precondition gate and reset contract.
 */
public final class Ca26Steps {
  private static final String DISPOSABLE_IDENTITY_CONTRACT =
      "CA26_DISPOSABLE_IDENTITY_CONTRACT";
  private static final String DISPOSABLE_APPLICATION_CONTRACT =
      "CA26_DISPOSABLE_APPLICATION_CONTRACT";
  private static final String DISPOSABLE_APPLICATION_ID =
      "CA26_DISPOSABLE_APPLICATION_ID";
  private static final String DISPOSABLE_RESET_CONTRACT =
      "CA26_DISPOSABLE_RESET_CONTRACT";
  private static final String SIGNING_BOUNDARY_APPROVED =
      "CA26_SIGNING_BOUNDARY_APPROVED";
  private static final String DOKOBIT_HANDOFF_URL =
      "CA26_DOKOBIT_HANDOFF_URL";
  private static final String EXPECTED_POST_SIGN_STATUS =
      "CA26_EXPECTED_POST_SIGN_STATUS";

  private enum Phase {
    START,
    PRECONDITIONS_VERIFIED,
    APPLICATION_BOUND,
    HANDOFF_READY,
    CONFIRMATION_GATED,
    POST_SIGN_STATUS_VERIFIED
  }

  private Phase phase = Phase.START;

  @Given("the CA-26 disposable identity, application, and reset contracts are approved")
  public void ca26_disposable_contracts_are_approved() {
    requireConfigured(
        DISPOSABLE_IDENTITY_CONTRACT,
        DISPOSABLE_APPLICATION_CONTRACT,
        DISPOSABLE_RESET_CONTRACT);
    phase = Phase.PRECONDITIONS_VERIFIED;
  }

  @And("the CA-26 external signing boundary is approved before confirmation")
  public void ca26_external_signing_boundary_is_approved_before_confirmation() {
    requirePhase(Phase.PRECONDITIONS_VERIFIED);
    requireExplicitApproval(SIGNING_BOUNDARY_APPROVED);
  }

  @When("I bind exactly one disposable CA-26 application")
  public void bind_exactly_one_disposable_ca26_application() {
    requirePhase(Phase.PRECONDITIONS_VERIFIED);
    requireConfigured(DISPOSABLE_APPLICATION_ID);
    phase = Phase.APPLICATION_BOUND;
  }

  @And("I prepare the external Dokobit handoff for that disposable CA-26 application")
  public void prepare_external_dokobit_handoff() {
    requirePhase(Phase.APPLICATION_BOUND);
    validateExternalDokobitHandoff(requireConfigured(DOKOBIT_HANDOFF_URL));
    phase = Phase.HANDOFF_READY;
  }

  @And("I hold the CA-26 signature confirmation at the approved boundary without executing it")
  public void hold_signature_confirmation_at_approved_boundary() {
    requirePhase(Phase.HANDOFF_READY);

    // Re-check every mutation prerequisite immediately before the irreversible
    // boundary. Missing or invalid contracts stop this flow before any
    // confirmation could be attempted.
    requireConfigured(
        DISPOSABLE_IDENTITY_CONTRACT,
        DISPOSABLE_APPLICATION_CONTRACT,
        DISPOSABLE_APPLICATION_ID,
        DISPOSABLE_RESET_CONTRACT,
        DOKOBIT_HANDOFF_URL);
    validateExternalDokobitHandoff(requireConfigured(DOKOBIT_HANDOFF_URL));
    requireExplicitApproval(SIGNING_BOUNDARY_APPROVED);

    // This compile-only flow intentionally ends at the external confirmation
    // boundary. No browser, provider request, or signature operation occurs.
    phase = Phase.CONFIRMATION_GATED;
  }

  @Then("the disposable CA-26 application exposes the approved post-sign status contract")
  public void disposable_ca26_application_exposes_post_sign_status_contract() {
    requirePhase(Phase.CONFIRMATION_GATED);
    String status = requireConfigured(EXPECTED_POST_SIGN_STATUS);
    if (status.length() > 64 || status.chars().anyMatch(Character::isISOControl)) {
      throw new AssertionError(
          "CA-26 fail-closed: the approved post-sign status contract is invalid: "
              + EXPECTED_POST_SIGN_STATUS);
    }
    phase = Phase.POST_SIGN_STATUS_VERIFIED;
  }

  private static void validateExternalDokobitHandoff(String handoffUrl) {
    try {
      URI uri = URI.create(handoffUrl);
      String host = uri.getHost();
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || host == null
          || !host.toLowerCase(Locale.ROOT).contains("dokobit")) {
        throw new IllegalArgumentException();
      }
    } catch (IllegalArgumentException invalidUrl) {
      // Never include the URL itself: a handoff may contain a sensitive token.
      throw new AssertionError(
          "CA-26 fail-closed: the external Dokobit handoff must be an HTTPS Dokobit URL: "
              + DOKOBIT_HANDOFF_URL);
    }
  }

  private static void requireExplicitApproval(String name) {
    String value = configuredValue(name);
    if (!("true".equalsIgnoreCase(value) || "approved".equalsIgnoreCase(value))) {
      throw new AssertionError(
          "CA-26 fail-closed before confirmation: explicit approval is missing: " + name);
    }
  }

  private static void requireConfigured(String... names) {
    List<String> missing = new ArrayList<>();
    for (String name : names) {
      if (configuredValue(name) == null) missing.add(name);
    }
    if (!missing.isEmpty()) {
      throw new AssertionError(
          "CA-26 fail-closed before confirmation: missing disposable contract(s): "
              + String.join(", ", missing));
    }
  }

  private static String requireConfigured(String name) {
    String value = configuredValue(name);
    if (value == null) {
      throw new AssertionError(
          "CA-26 fail-closed before confirmation: missing disposable contract: " + name);
    }
    return value;
  }

  private static String configuredValue(String name) {
    String systemProperty = System.getProperty(name);
    if (systemProperty != null && !systemProperty.isBlank()) return systemProperty.trim();
    String environmentValue = System.getenv(name);
    if (environmentValue != null && !environmentValue.isBlank()) return environmentValue.trim();
    return null;
  }

  private void requirePhase(Phase expected) {
    if (phase != expected) {
      throw new AssertionError(
          "CA-26 strict flow out of order: expected " + expected + " but was " + phase);
    }
  }
}
