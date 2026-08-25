package steps;

import io.cucumber.java.en.Given;

/**
 * Starts mutation scenarios through the same proven admin authentication path
 * used by the golden INT scenarios, then delegates the disposable lifecycle to
 * CA-23. This avoids treating a still-rendering /corporate-actions page as an
 * unauthenticated state.
 */
public final class AdminDraftPrerequisitesV2 {
  private static final String MARKER = "CA23_DISPOSABLE_DRAFT_20260817";

  private final Ca23Steps draft;
  private final AdminSteps admin;

  public AdminDraftPrerequisitesV2(Ca23Steps draft, AdminSteps admin) {
    this.draft = draft;
    this.admin = admin;
  }

  @Given("a fresh authenticated disposable admin Corporate Actions draft exists")
  public void freshAuthenticatedDisposableAdminDraft() {
    admin.i_am_authenticated_in_the_admin_application();
    draft.open_cleanup_preflight_list();
    draft.prove_cleanup_contract_before_save();
    draft.open_creation_surface_without_saving();
    draft.choose_observed_company("LV");
    draft.choose_observed_form("bonus");
    draft.enter_deterministic_marker(MARKER);
    draft.save_exactly_one_disposable_draft();
    draft.assert_saved_marker_and_status(MARKER, "Draft");
  }
}
