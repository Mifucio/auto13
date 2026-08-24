@req:CA-39
Feature: "Attach a file in Attachments tab"
  Requirement CA-39 grounded at /corporate-actions/form via goto /login · login(Log In) · submit · goto /corporate-actions/form

  @req:CA-39
  Scenario: [admin] "Attach a file in Attachments tab"
    Given I navigate to the admin "/login"
    And I am on the admin application
    And I submit the observed form
    And I navigate to the admin "/corporate-actions/form"
    When I click "Open application"
    And I click "Open attachments tab"
    And I click "Attach file"
    When I click "Open application"
    And I click "Open attachments tab"
    And I click "Attach file"
    Then attachment_added

