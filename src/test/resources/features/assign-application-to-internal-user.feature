@req:CA-33
Feature: "Assign Application to internal user"
  Requirement CA-33 grounded at /corporate-actions/form via goto /login · login(Log In) · submit · goto /corporate-actions/form

  @req:CA-33
  Scenario: [admin] "Assign Application to internal user"
    Given I navigate to the admin "/login"
    And I am on the admin application
    And I submit the observed form
    And I navigate to the admin "/corporate-actions/form"
    When I click "Open application"
    And I click "Assign internal user"
    And I click "Save assignment"
    When I click "Open application"
    And I click "Assign internal user"
    And I click "Save assignment"
    Then assignment_saved

