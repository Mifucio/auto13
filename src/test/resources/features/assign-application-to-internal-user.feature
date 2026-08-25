@req:CA-33
@cross_surface_disposable_admin
Feature: "Assign Application to internal user"

  @req:CA-33
  Scenario: [admin] "Assign Application to internal user"
    Given a fresh disposable customer Bonus Issue draft is opened in the admin application
    When I assign the disposable application to an available internal user
    Then the disposable assignment is saved
