@req:CA-34
@direct_ca_readonly
Feature: "View single application"
  Requirement CA-34 grounded at /corporate-actions/form via direct admin login · observed existing application from /corporate-actions

  @req:CA-34
  Scenario: [admin] "View single application"
    Given I navigate to the admin "/login"
    And I am on the admin application
    And I submit the observed form
    And I navigate to the admin "/corporate-actions"
    When I open the observed existing Corporate Actions application for "LV", "bonus" without saving
    Then application_details_visible
