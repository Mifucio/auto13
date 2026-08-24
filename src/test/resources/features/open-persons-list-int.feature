@req:BP-15
Feature: "Open Persons list (INT)"
  Requirement BP-15 grounded at /external/admin/persons via goto /login · login(Log In) · submit · goto /external/admin/persons

  @req:BP-15
  @direct_focus
  Scenario: [admin] "Open Persons list"
    Given I am authenticated in the admin application
    And I navigate to the admin "/external/admin/persons"
    Then the admin page "/external/admin/persons" is displayed with "persons"
