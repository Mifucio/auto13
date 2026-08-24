@req:BP-18
Feature: "Edit Person (INT)"
  Requirement BP-18 grounded at /holders-information/ereg/person-isins via the proven admin authentication path and one observed person.

  @req:BP-18
  @direct_focus
  @direct_persons
  Scenario: [admin] "Edit Person"
    Given I am authenticated in the admin application
    And I navigate to the admin "/holders-information/ereg/person-isins"
    When I search for the observed person "VIESTURS LOKMANIS" without saving
    And I open the observed person editor without saving
    Then person_edit_workflow_without_saving
