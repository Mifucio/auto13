@req:CA-33
@direct_ca_disposable_draft
Feature: "Assign Application to internal user"

  @req:CA-33
  Scenario: [admin] "Assign Application to internal user"
    Given a fresh disposable admin Corporate Actions draft exists
    When I assign the disposable application to an available internal user
    Then the disposable assignment is saved
