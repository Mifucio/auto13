@req:CA-27
@direct_ca_readonly
Feature: "View History tab"
  Requirement CA-27 grounded at /corporate-actions/form via direct admin login · observed existing application from /corporate-actions

  @req:CA-27
  Scenario Outline: [admin] "View History tab" for <company>, <caForm>
    Given I navigate to the admin "/login"
    And I am on the admin application
    And I submit the observed form
    And I navigate to the admin "/corporate-actions"
    When I open the observed existing Corporate Actions application for "<company>", "<caForm>" without saving
    And I open the observed "History" Corporate Actions tab without saving
    Then history_entries_visible

    # One observed application preserves the tab-view acceptance semantics
    # without claiming fabricated company/form records.
    Examples:
      | company | caForm |
      | LV | bonus |
