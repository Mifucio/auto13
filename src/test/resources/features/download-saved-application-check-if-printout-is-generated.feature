@req:CA-29
Feature: "Download saved application - check if printout is generated"
  Requirement CA-29 uses the current live Submitted list and stays fail-closed if the visible printout control produces no artifact.

  @req:CA-29
  @direct_ca_printout
  Scenario: [admin] "Download saved application - check if printout is generated"
    Given I am authenticated in the admin application
    And I navigate to the admin "/corporate-actions"
    When I observe a current Submitted Corporate Actions application with form "Bonus Issue"
    Then the current Corporate Actions Fillable PDF printout download exists
