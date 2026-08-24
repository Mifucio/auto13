@req:CA-36
@direct_ca_readonly
Feature: "View Signatures tab"
  Requirement CA-36 uses the proven admin authentication path and one observed existing application.

  @req:CA-36
  Scenario: [admin] "View Signatures tab"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I open the observed existing Corporate Actions application for "LV", "bonus" without saving
    And I open the observed "Signatures" Corporate Actions tab without saving
    Then signatures_visible
