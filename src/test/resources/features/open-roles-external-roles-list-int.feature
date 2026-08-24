@req:BP-11
Feature: "Open Roles → External roles list (INT)"
  Requirement BP-11 grounded at /external/admin/authority-rights via goto /login · login(Log In) · submit · goto /external/admin/authority-rights

  @req:BP-11
  @direct_focus
  @external_roles_list
  Scenario: [admin] "Open Roles → External roles list"
    Given I am authenticated in the admin application
    And I navigate to the admin "/external/admin/authority-rights"
    Then the admin page "/external/admin/authority-rights" is displayed with "External Roles"
