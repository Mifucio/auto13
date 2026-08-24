@req:CA-35
Feature: "Download application"
  Requirement CA-35 uses the proven admin authentication path and the newest visible Submitted Bonus Issue application.

  @req:CA-35
  @direct_ca_download
  Scenario: [admin] "Download application"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I open the latest visible Submitted Corporate Actions application with form "Bonus Issue"
    And I download the latest observed Corporate Actions application
    Then the latest observed Corporate Actions application download exists
