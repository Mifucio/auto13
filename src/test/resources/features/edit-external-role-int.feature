@req:BP-12
Feature: "Edit external role (INT)"
  Requirement BP-12 opens the observed role editor, modifies the Description (add "A1" after "tests"), saves, then restores and saves again — net-zero change.

  @req:BP-12
  @direct_management
  @edit_external_role
  Scenario: [admin] "Edit external role — modify and restore Description"
    Given I am authenticated in the admin application
    And I navigate to the admin "/external/admin/authority-rights"
    When I find and open the observed external role "AutotestRole" editor
    Then the external role editor shows the role "AutotestRole"
    When I remember the role Description and append " A1" after the word "tests"
    And I click "Save" on the role editor
    Then the admin page "/external/admin/authority-rights" is displayed with "External Roles"
    When I find and open the observed external role "AutotestRole" editor again
    When I restore the original role Description
    And I click "Save" on the role editor
    Then the admin page "/external/admin/authority-rights" is displayed with "External Roles"
