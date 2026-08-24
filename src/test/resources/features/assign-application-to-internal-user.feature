@req:CA-33
Feature: "Assign Application to internal user"
  Requirement CA-33 uses the proven admin authentication path and the observed application assignment surface.

  @req:CA-33
  Scenario: [admin] "Assign Application to internal user"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions/form"
    When I click "Open application"
    And I click "Assign internal user"
    And I click "Save assignment"
    Then assignment_saved
