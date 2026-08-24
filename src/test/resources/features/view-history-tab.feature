@req:CA-27
@direct_ca_readonly
Feature: "View History tab"
  Requirement CA-27 uses the proven admin authentication path and the newest visible Submitted Bonus Issue application.

  @req:CA-27
  Scenario: [admin] "View History tab" for current Bonus Issue application
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I open the latest visible Submitted Corporate Actions application with form "Bonus Issue"
    And I open the latest observed Corporate Actions "History" tab
    Then the latest observed Corporate Actions "History" tab is active
