@req:CA-38
@direct_ca_readonly
Feature: "View Attachments tab"
  Requirement CA-38 grounded at /corporate-actions/form via direct admin login · observed existing application from /corporate-actions

  @req:CA-38
  Scenario: [admin] "View Attachments tab"
    Given I navigate to the admin "/login"
    And I am on the admin application
    And I submit the observed form
    And I navigate to the admin "/corporate-actions"
    When I open the observed existing Corporate Actions application for "LV", "bonus" without saving
    And I open the observed "Attachments" Corporate Actions tab without saving
    Then attachments_list_visible
