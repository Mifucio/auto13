@req:BP-12
Feature: "Edit external role (INT)"
  Requirement BP-12 opens the observed role editor and verifies it without changing shared role data.

  @req:BP-12
  @direct_management
  @edit_external_role
  Scenario: [admin] "Edit external role"
    Given I am authenticated in the admin application
    And I navigate to the admin "/external/admin/authority-rights"
    When I fill "Search query" with "AutotestRole"
    And I click "Search"
    And I open the observed external role "AutotestRole" editor
    Then the opened external role "AutotestRole" editor remains visible without saving
