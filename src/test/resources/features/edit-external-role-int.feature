@req:BP-12
Feature: "Edit external role (INT)"
  Requirement BP-12 grounded at /external/admin/authority-rights via the proven admin authentication path and the observed role editor.

  @req:BP-12
  @direct_management
  @edit_external_role
  Scenario: [admin] "Edit external role"
    Given I am authenticated in the admin application
    And I navigate to the admin "/external/admin/authority-rights"
    When I fill "Search query" with "AutotestRole"
    And I click "Search"
    And I open the observed external role "AutotestRole" editor
    Then the observed external role editor is displayed
