@req:BP-14
Feature: "Edit internal role (INT)"
  Requirement BP-14 opens the internal role editor, modifies the Description
  (add "1" after "Admin role for automated tests"), removes one selected right,
  saves, then restores and saves again — net-zero change.

  @req:BP-14
  @direct_management
  Scenario: [admin] "Edit internal role — modify Description and remove/restore a right"
    Given I am authenticated in the admin application
    And I navigate to the admin "/admin/authority-rights"
    When I find and open the observed internal role "AutoTestAdmin" editor
    Then the internal role editor shows the role "AutoTestAdmin"
    When I remember the internal role state
    And I append "1" after "automated tests" in the internal role Description
    And I check the first selected right checkbox
    Then the Remove rights button becomes enabled
    When I click the Remove rights button
    Then the selected rights count decreases by one
    When I click "Save" on the internal role editor
    Then the admin page "/admin/authority-rights" is displayed with "Internal Roles"
    When I find and open the observed internal role "AutoTestAdmin" editor again
    And I restore the internal role Description
    And I add the previously removed right back
    And I click "Save" on the internal role editor
    Then the admin page "/admin/authority-rights" is displayed with "Internal Roles"
