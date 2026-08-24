@req:BP-13
Feature: "Open Roles → Internal roles list (INT)"
  Requirement BP-13 grounded at /admin/authority-rights via goto /login · login(Log In) · submit · goto /admin/authority-rights

  @req:BP-13
  @direct_focus
  Scenario: [admin] "Open Roles → Internal roles list"
    Given I am authenticated in the admin application
    And I navigate to the admin "/admin/authority-rights"
    Then the admin page "/admin/authority-rights" is displayed with "Internal Roles"
