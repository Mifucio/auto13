@req:BP-08
Feature: "Search External user (INT)"
  Requirement BP-08 grounded at /external/admin/persons via goto /login · login(Log In) · submit · goto /external/admin/persons

  @req:BP-08
  @direct_management
  @search_external_user
  Scenario: [admin] "Search External user"
    Given I navigate to the admin "/login"
    And I am on the admin application
    And I submit the observed form
    And I navigate to the admin "/external/admin/persons"
    When I fill "Search query" with "Autotests"
    And I submit the observed form
    Then search_results_visible
