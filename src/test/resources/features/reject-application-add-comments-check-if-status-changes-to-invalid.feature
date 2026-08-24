@req:CA-40
Feature: "Reject Application, add comments, check if status changes to Invalid"
  Requirement CA-40 grounded at /corporate-actions/form via goto /login · login(Log In) · submit · goto /corporate-actions/form

  @req:CA-40
  Scenario: [admin] "Reject Application, add comments, check if status changes to Invalid"
    Given I navigate to the admin "/login"
    And I am on the admin application
    And I submit the observed form
    And I navigate to the admin "/corporate-actions/form"
    When I click "Open application"
    And I click "Reject application"
    And I click "Add comment"
    And I click "Confirm reject"
    When I click "Open application"
    And I click "Reject application"
    And I click "Add comment"
    And I click "Confirm reject"
    Then status_invalid

