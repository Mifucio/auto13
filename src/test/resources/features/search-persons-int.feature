@req:BP-16
Feature: "Search Persons (INT)"
  Requirement BP-16 grounded at /external/admin/persons via goto /login · login(Log In) · submit · goto /external/admin/persons

  @req:BP-16
  @direct_focus
  @direct_persons
  Scenario: [admin] "Search Persons"
    Given I am authenticated in the admin application
    And I navigate to the admin "/external/admin/persons"
    When I fill "Search query" with "Autotests"
    And I submit the observed form
    Then person_search_results
    And the persons search result list contains "Autotest"
    When I log out from the admin application
