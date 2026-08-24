@req:CA-40
Feature: "Reject Application, add comments, check if status changes to Invalid"
  Requirement CA-40 uses the proven admin authentication path and performs the rejection once.

  @req:CA-40
  Scenario: [admin] "Reject Application, add comments, check if status changes to Invalid"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions/form"
    When I click "Open application"
    And I click "Reject application"
    And I click "Add comment"
    And I click "Confirm reject"
    Then status_invalid
