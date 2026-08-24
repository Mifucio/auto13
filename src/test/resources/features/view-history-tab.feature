@req:CA-27
@direct_ca_readonly
Feature: "View History tab"
  Requirement CA-27 uses the proven admin authentication path and one observed existing application.

  @req:CA-27
  Scenario Outline: [admin] "View History tab" for <company>, <caForm>
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I open the observed existing Corporate Actions application for "<company>", "<caForm>" without saving
    And I open the observed "History" Corporate Actions tab without saving
    Then history_entries_visible

    Examples:
      | company | caForm |
      | LV | bonus |
