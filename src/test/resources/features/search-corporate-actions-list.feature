@req:CA-32
Feature: "Search Corporate actions list"
  Requirement CA-32 uses the proven admin authentication path and the observed Corporate Actions filter.

  @req:CA-32
  @direct_ca_filter
  Scenario: [admin] "Search Corporate actions list"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I filter the observed corporate actions list by form "Bonus Issue"
    Then corporate_actions_filter_results_visible
