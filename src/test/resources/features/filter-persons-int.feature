@req:BP-17
Feature: "Filter Persons (INT)"
  Requirement BP-17 grounded at /external/admin/persons via goto /login · login(Log In) · submit · goto /external/admin/persons

  @req:BP-17
  @direct_focus
  @direct_persons
  Scenario: [admin] "Filter Persons"
    Given I am authenticated in the admin application
    And I navigate to the admin "/external/admin/persons"
    When I click "Form"
    And I select observed person form "Natural person"
    And I submit the observed form
    Then filtered_persons_list
