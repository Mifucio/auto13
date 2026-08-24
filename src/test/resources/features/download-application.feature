@req:CA-35
Feature: "Download application"
  Requirement CA-35 grounded at /corporate-actions/form via goto /login · login(Log In) · submit · goto /corporate-actions/form

  @req:CA-35
  @direct_ca_download
  Scenario: [admin] "Download application"
    Given I navigate to the admin "/login"
    And I am on the admin application
    And I submit the observed form
    And I navigate to the admin "/corporate-actions"
    When I open the observed existing Corporate Actions application for "LV", "bonus" without saving
    And I download the observed application
    Then file_downloaded
