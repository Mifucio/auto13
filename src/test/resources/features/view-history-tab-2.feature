@req:CA-37
@direct_ca_readonly
Feature: "View History tab"
  Requirement CA-37 uses the proven admin authentication path and one observed existing application.

  @req:CA-37
  Scenario: [admin] "View History tab"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I open the observed existing Corporate Actions application for "LV", "bonus" without saving
    And I open the observed "History" Corporate Actions tab without saving
    Then history_entries_visible
