@req:BP-18
Feature: "Edit Person (INT)"
  Requirement BP-18 opens a person from the External Persons list, changes
  status to Disabled, appends "1" to Name, saves, then restores.

  @req:BP-18
  @direct_focus
  @direct_persons
  Scenario: [admin] "Edit Person — modify Name and Status"
    Given I am authenticated in the admin application
    And I navigate to the admin "/external/admin/persons"
    When I search for person "Autotests"
    And I open the first person from the search results
    Then the person editor is displayed
    When I remember the person state
    And I append "1" after the person Name
    And I change person status to "Disabled"
    When I click "Save" on the person editor
    And I search for person "Autotests" again
    And I open the first person from the search results again
    And I restore the person Name
    And I change person status to "Active"
    When I click "Save" on the person editor
    Then the person editor save is confirmed
