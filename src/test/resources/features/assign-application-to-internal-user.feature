@req:CA-33
@ca33_assignment_repair
Feature: "Assign Application to internal user"

  @req:CA-33
  Scenario: [admin] "Assign Application to internal user"
    Given a fresh authenticated disposable admin Corporate Actions draft exists
    When I assign the disposable application to an available internal user
    Then the disposable assignment is saved
