@req:CA-32
Feature: "Search Corporate actions list"
  Requirement CA-32 is one scenario covering both search options of the observed Corporate Actions list: search by form checkbox and by search word (ISIN, Issuer name).

  @req:CA-32
  @direct_ca_filter
  @direct_ca_search_word
  Scenario: [admin] "Search Corporate actions list"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I filter the observed corporate actions list by form "Bonus Issue"
    Then corporate_actions_filter_results_visible
    And I search the observed corporate actions list by "NES2048"
    Then corporate_actions_search_results_visible