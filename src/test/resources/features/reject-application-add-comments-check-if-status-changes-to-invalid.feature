@req:CA-40
@direct_ca_disposable_draft
Feature: "Reject Application, add comments, check if status changes to Invalid"

  @req:CA-40
  Scenario: [admin] "Reject Application, add comments, check if status changes to Invalid"
    Given a fresh authenticated disposable admin Corporate Actions draft exists
    When I reject the disposable application with comment "Automated test rejection"
    Then the disposable application status is Invalid
