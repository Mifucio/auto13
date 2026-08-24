@req:CA-38
@direct_ca_readonly
Feature: "View Attachments tab"
  Requirement CA-38 uses the proven admin authentication path and one observed existing application.

  @req:CA-38
  Scenario: [admin] "View Attachments tab"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I open the observed existing Corporate Actions application for "LV", "bonus" without saving
    And I open the observed "Attachments" Corporate Actions tab without saving
    Then attachments_list_visible
