@req:BP-07
Feature: "Open Users → External users list (INT)"
  Requirement BP-07 grounded at /external/admin/persons via goto /login · login(Log In) · submit · goto /external/admin/persons

  @req:BP-07
  @direct_focus
  @external_users_list
  Scenario: [admin] "Open Users → External users list"
    Given I am authenticated in the admin application
    And I navigate to the admin "/external/admin/persons"
    Then the admin page "/external/admin/persons" is displayed with "persons"
