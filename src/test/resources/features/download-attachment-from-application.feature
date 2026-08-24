@req:CA-24
Feature: "Download attachment from application"
  Requirement CA-24 grounded at the authenticated /corporate-actions list, then the observed application /corporate-actions/application-form/{id} Attachments tab.

  @direct_ca_readonly
  Scenario: [admin] download one observed uploaded attachment without changing application data
    Given I navigate to the admin "/login"
    And I am on the admin application
    And I submit the observed form
    And I navigate to the admin "/corporate-actions"
    When I find and download an observed uploaded attachment without changing application data
    Then ca24_attachment_downloaded
