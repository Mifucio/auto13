@req:CA-25
@cross_surface_disposable_admin
Feature: "Initiate signature process, view signees"
  Requirement CA-25 is exercised on one disposable customer-created application opened in admin.

  @req:CA-25
  Scenario: [admin] "Initiate signature process, view signees"
    Given a fresh disposable customer Bonus Issue draft is opened in the admin application
    When I initiate signing and open the disposable signees surface
    Then the disposable signees surface is visible
