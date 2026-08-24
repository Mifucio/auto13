@req:CA-24
Feature: "Download attachment from application"
  Requirement CA-24 uses the authenticated Corporate Actions list and one observed uploaded attachment.

  @direct_ca_readonly
  Scenario: [admin] download one observed uploaded attachment without changing application data
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I find and download an observed uploaded attachment without changing application data
    Then ca24_attachment_downloaded
