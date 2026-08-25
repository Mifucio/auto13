@req:CA-40
@cross_surface_disposable_admin
Feature: "Reject Application, add comments, check if status changes to Invalid"

  @req:CA-40
  Scenario: [admin] "Reject Application, add comments, check if status changes to Invalid"
    Given a fresh disposable customer Bonus Issue draft is opened in the admin application
    When I reject the disposable application with comment "Automated test rejection"
    Then the disposable application status is Invalid
