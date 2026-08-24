@req:CA-25
Feature: "Initiate signature process, view signees"
  Requirement CA-25 uses the proven admin authentication path and performs the signing setup once per example.

  @req:CA-25
  Scenario Outline: [admin] "Initiate signature process, view signees" for <company>, <caForm>
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions/form"
    When I click "Open application"
    And I click "Initiate signature"
    And I click "View signees"
    And I select "<company>" from "Company"
    And I select "<caForm>" from "Corporate action form"
    Then signees_list_visible

    Examples:
      | company | caForm |
      | EE | dividend |
      | EE | bonus |
      | EE | interest |
      | EE | additional_bonds |
      | IS | dividend |
      | IS | bonus |
      | IS | interest |
      | IS | additional_bonds |
      | LT | dividend |
      | LT | bonus |
      | LT | interest |
      | LT | additional_bonds |
      | LV | dividend |
      | LV | bonus |
      | LV | interest |
      | LV | additional_bonds |
