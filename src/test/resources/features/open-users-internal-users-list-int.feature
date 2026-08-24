@req:BP-09
Feature: "Open Users → Internal users list (INT)"
  Requirement BP-09 grounded at /admin/user-management via goto /login · login(Log In) · submit · goto /admin/user-management

  @req:BP-09
  @direct_focus
  @internal_users_list
  Scenario: [admin] "Open Users → Internal users list"
    Given I am authenticated in the admin application
    And I navigate to the admin "/admin/user-management"
    Then the admin page "/admin/user-management" is displayed with "user"
