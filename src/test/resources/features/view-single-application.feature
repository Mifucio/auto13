@req:CA-34
@direct_ca_readonly
Feature: "View single application"
  Requirement CA-34 uses the proven admin authentication path and one observed existing application.

  @req:CA-34
  Scenario: [admin] "View single application"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I open the observed existing Corporate Actions application for "LV", "bonus" without saving
    Then application_details_visible
