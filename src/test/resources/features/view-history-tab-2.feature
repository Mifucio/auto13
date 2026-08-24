@req:CA-37
@direct_ca_readonly
Feature: "View History tab"
  Requirement CA-37 grounded at /corporate-actions/form via direct admin login · observed existing application from /corporate-actions

  @req:CA-37
  Scenario: [admin] "View History tab"
    Given I navigate to the admin "/login"
    And I am on the admin application
    And I submit the observed form
    And I navigate to the admin "/corporate-actions"
    When I open the observed existing Corporate Actions application for "LV", "bonus" without saving
    And I open the observed "History" Corporate Actions tab without saving
    Then history_entries_visible
