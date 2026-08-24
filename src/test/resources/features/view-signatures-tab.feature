@req:CA-36
@direct_ca_readonly
Feature: "View Signatures tab"
  Requirement CA-36 grounded at /corporate-actions/form via direct admin login · observed existing application from /corporate-actions

  @req:CA-36
  Scenario: [admin] "View Signatures tab"
    Given I navigate to the admin "/login"
    And I am on the admin application
    And I submit the observed form
    And I navigate to the admin "/corporate-actions"
    When I open the observed existing Corporate Actions application for "LV", "bonus" without saving
    And I open the observed "Signatures" Corporate Actions tab without saving
    Then signatures_visible
