@req:CA-39
Feature: "Attach a file in Attachments tab"
  Requirement CA-39 uses the proven admin authentication path and performs the attachment action once.

  @req:CA-39
  Scenario: [admin] "Attach a file in Attachments tab"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions/form"
    When I click "Open application"
    And I click "Open attachments tab"
    And I click "Attach file"
    Then attachment_added
