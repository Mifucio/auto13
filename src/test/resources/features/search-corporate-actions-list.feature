@req:CA-32
Feature: "Search Corporate actions list"
  Requirement CA-32 grounded at /corporate-actions via goto /login · login(Log In) · submit · goto /corporate-actions

  @req:CA-32
  @direct_ca_filter
  Scenario: [admin] "Search Corporate actions list"
    Given I navigate to the admin "/login"
    And I am on the admin application
    And I submit the observed form
    And I navigate to the admin "/corporate-actions"
    When I filter the observed corporate actions list by form "Bonus Issue"
    Then corporate_actions_filter_results_visible
