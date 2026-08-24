@req:CA-36
@direct_ca_readonly
Feature: "View Signatures tab"
  Requirement CA-36 uses the proven admin authentication path and the newest visible Submitted Bonus Issue application.

  @req:CA-36
  Scenario: [admin] "View Signatures tab"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I open the latest visible Submitted Corporate Actions application with form "Bonus Issue"
    And I open the latest observed Corporate Actions "Signatures" tab
    Then the latest observed Corporate Actions "Signatures" tab is active
