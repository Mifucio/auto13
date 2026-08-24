@req:CA-29
Feature: "Download saved application - check if printout is generated"
  Requirement CA-29 grounded at /corporate-actions via observed admin login · existing submitted application selector · artifact boundary

  @req:CA-29
  @direct_ca_printout
  Scenario: [admin] "Download saved application - check if printout is generated"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I select the observed existing Corporate Actions application for "LV", "bonus" without saving
    Then ca29_artifact_contract_boundary_visible
