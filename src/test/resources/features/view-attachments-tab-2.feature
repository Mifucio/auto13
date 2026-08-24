@req:CA-38
@direct_ca_readonly
Feature: "View Attachments tab"
  Requirement CA-38 uses the proven admin authentication path and the newest visible Submitted Bonus Issue application.

  @req:CA-38
  Scenario: [admin] "View Attachments tab"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I open the latest visible Submitted Corporate Actions application with form "Bonus Issue"
    And I open the latest observed Corporate Actions "Attachments" tab
    Then the latest observed Corporate Actions "Attachments" tab is active
