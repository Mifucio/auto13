@req:CA-35
Feature: "Download application"
  Requirement CA-35 uses the proven admin authentication path and one observed existing application.

  @req:CA-35
  @direct_ca_download
  Scenario: [admin] "Download application"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I open the observed existing Corporate Actions application for "LV", "bonus" without saving
    And I download the observed application
    Then file_downloaded
