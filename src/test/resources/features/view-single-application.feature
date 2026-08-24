@req:CA-34
@direct_ca_readonly
Feature: "View single application"
  Requirement CA-34 uses the proven admin authentication path and the newest visible Submitted Bonus Issue application.

  @req:CA-34
  Scenario: [admin] "View single application"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I open the latest visible Submitted Corporate Actions application with form "Bonus Issue"
    Then the latest observed Corporate Actions application details are visible
